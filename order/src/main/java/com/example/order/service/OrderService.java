package com.example.order.service;

import com.example.order.api.InventoryApi;
import com.example.order.api.PaymentApi;
import com.example.order.config.RabbitQueue;
import com.example.order.constants.OrderStatus;
import com.example.order.constants.OutboxMessageStatus;
import com.example.order.dto.SeatDto;
import com.example.order.dto.CreateOrderInDto;
import com.example.order.dto.CreateOrderOutDto;
import com.example.order.entity.EmailOutboxMessage;
import com.example.order.entity.Order;
import com.example.order.entity.OutboxMessage;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.InvalidSeatSelectionException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.PaymentAlreadyInitiatedException;
import com.example.order.repository.EmailOutboxRepository;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryApi inventoryApi;
    private final PaymentApi paymentApi;
    private final OutboxMessageRepository outboxMessageRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // Must match the TTL on RabbitConfig.unpaidOrderCheckQueue.
    private static final java.time.Duration HOLD_TTL = java.time.Duration.ofMinutes(10);

    public CreateOrderOutDto createOrder(String userId, String idempotencyKey,
                                         CreateOrderInDto createOrderInDto) {
        // HTTP idempotency: an earlier call with the same (userId, idempotencyKey)
        // already completed. Return that order's response without touching inventory.
        // Status is intentionally not filtered — a CANCELLED match still wins, because
        // the contract is "same key → same outcome." Client can inspect the status.
        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toCreateOrderResponse(existing.get());
        }

        long distinctRows = createOrderInDto.seats().stream()
                .map(s -> s.get("section") + ":" + s.get("row"))
                .distinct()
                .count();
        if (distinctRows > 1) {
            throw new InvalidSeatSelectionException("All seats in an order must belong to the same section and row");
        }

        // Same key flows through to the inventory hold so any in-flight Spring Retry
        // replay on this createOrder also dedups on the inventory side.
        InventoryApi.InventoryHoldResponse response = inventoryApi.holdSeats(
                createOrderInDto.eventId(), createOrderInDto.seats(), userId, idempotencyKey);

        LocalDateTime expiresAt = LocalDateTime.now().plus(HOLD_TTL);

        Order createdOrder;
        try {
            createdOrder = transactionTemplate.execute(status -> {
                Order order = new Order();
                order.setEventId(createOrderInDto.eventId());
                order.setUserId(userId);
                order.setUserEmail(createOrderInDto.userEmail());
                order.setSeats(response.seats());
                order.setAmount(calculateAmount(response.seats()));
                order.setStatus(OrderStatus.PENDING);
                order.setHoldId(response.holdId());
                order.setExpiresAt(expiresAt);
                order.setIdempotencyKey(idempotencyKey);

                orderRepository.save(order);
                log.info("Order created. orderId={}, eventId={}", order.getId(), order.getEventId());

                outboxMessageRepository.save(outboxEntry(
                        RabbitQueue.ORDER_EXCHANGE,
                        RabbitQueue.ORDER_PENDING_CREATED_KEY,
                        Map.of("orderId", order.getId())));

                return order;
            });
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // A concurrent request with the same idempotency key (or the same holdId,
            // which can happen when both calls hit the inventory idem path) won the
            // INSERT race. The hold legitimately belongs to that winner — do NOT
            // compensate-release it. Just return their order.
            Optional<Order> raced = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (raced.isPresent()) {
                log.info("Lost INSERT race to concurrent idempotent request. orderId={}", raced.get().getId());
                return toCreateOrderResponse(raced.get());
            }
            log.error("Order INSERT hit a constraint violation that wasn't a duplicate idem key. holdId={}",
                    response.holdId(), e);
            compensatingRelease(createOrderInDto.eventId(), response.seats(), response.holdId());
            throw new RuntimeException("Order creation failed, please retry", e);
        } catch (Exception e) {
            log.error("Order DB write failed. Compensating release for holdId={}", response.holdId(), e);
            compensatingRelease(createOrderInDto.eventId(), response.seats(), response.holdId());
            throw new RuntimeException("Order creation failed, please retry", e);
        }

        if (createdOrder == null) {
            log.error("Order transaction was rolled back. Compensating release for holdId={}", response.holdId());
            compensatingRelease(createOrderInDto.eventId(), response.seats(), response.holdId());
            throw new RuntimeException("Order creation failed, please retry");
        }

        return toCreateOrderResponse(createdOrder);
    }

    private CreateOrderOutDto toCreateOrderResponse(Order order) {
        java.time.Instant expiresAt = order.getExpiresAt() == null
                ? null
                : order.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
        return new CreateOrderOutDto(order.getId(), order.getHoldId(), expiresAt);
    }

    private void compensatingRelease(int eventId, List<SeatDto> seats, String holdId) {
        if (seats == null || seats.isEmpty()) return;
        SeatDto first = seats.get(0);
        List<Integer> seatNumbers = seats.stream().map(SeatDto::number).toList();
        try {
            inventoryApi.releaseHold(eventId, first.section(), first.row(), holdId, seatNumbers);
        } catch (Exception ex) {
            // Orphan reconciler in inventory will eventually clean this up.
            log.error("Compensating release failed. holdId={} — orphan reconciler will clean up", holdId, ex);
        }
    }

    // Must match the TTL on RabbitConfig.paymentWindowCheckQueue.
    private static final java.time.Duration PAYMENT_WINDOW = java.time.Duration.ofMinutes(5);

    public PaymentApi.PaymentResponse processPayment(String userId, Long orderId, String confirmationTokenId) {
        // Step 1 (locked, short tx): PENDING -> AWAITING_PAYMENT. Extending payment_window_expires_at
        // and publishing the payment-window check happen here so the deadline can never slip
        // past the row's commit.
        Order locked = transactionTemplate.execute(s -> {
            Order o = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            // Ownership check inside the row lock. 404 instead of 403 so an attacker
            // can't probe for valid order IDs they don't own.
            if (!Objects.equals(o.getUserId(), userId)) {
                throw new OrderNotFoundException(orderId);
            }

            if (o.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderStateException("Order is not in pending state");
            }
            if (o.getExpiresAt() != null && o.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new InvalidOrderStateException("Order has expired");
            }
            if (o.getPaymentIntentId() != null) {
                throw new PaymentAlreadyInitiatedException();
            }

            LocalDateTime now = LocalDateTime.now();
            o.setStatus(OrderStatus.AWAITING_PAYMENT);
            o.setPaymentInitiatedAt(now);
            o.setPaymentWindowExpiresAt(now.plus(PAYMENT_WINDOW));
            Order saved = orderRepository.save(o);

            outboxMessageRepository.save(outboxEntry(
                    RabbitQueue.ORDER_EXCHANGE,
                    RabbitQueue.PAYMENT_WINDOW_INITIATED_KEY,
                    Map.of("orderId", orderId)));

            return saved;
        });

        // Step 2 (no lock, no tx): Stripe call. The hold-active check is dropped — the row lock
        // in step 1 plus the expires_at check proved the hold was live at transition time, and
        // payment_window_expires_at owns the deadline from here.
        PaymentApi.PaymentResponse response = paymentApi.processPayment(
                orderId, locked.getAmount(), "cad", confirmationTokenId);

        // Step 3 (locked, short tx): reconcile based on Stripe response.
        return reconcile(orderId, response);
    }

    private PaymentApi.PaymentResponse reconcile(Long orderId, PaymentApi.PaymentResponse response) {
        return transactionTemplate.execute(s -> {
            Order o = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

            if (o.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                log.error("reconcile found unexpected state. orderId={}, status={}, paymentIntentId={}",
                        orderId, o.getStatus(), response.paymentIntentId());
                return response;
            }

            switch (response.status()) {
                case "succeeded" -> {
                    o.setPaymentIntentId(response.paymentIntentId());
                    o.setStatus(OrderStatus.PROCESSING);
                    orderRepository.save(o);
                    outboxMessageRepository.save(buildOrderPaidOutbox(o));
                    eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.PROCESSING));
                }
                case "failed" -> {
                    // Revert to PENDING so the user can retry with a fresh confirmation token.
                    // Hold stays intact (seats remain reserved); order TTL bounds the retry window.
                    // Clear payment_window_expires_at — any in-flight payment-window message will
                    // see status != AWAITING_PAYMENT and no-op.
                    o.setStatus(OrderStatus.PENDING);
                    o.setPaymentInitiatedAt(null);
                    o.setPaymentWindowExpiresAt(null);
                    orderRepository.save(o);
                }
                case "requires_action" -> {
                    // 3DS / SCA — Stripe fires a webhook later. Stay in AWAITING_PAYMENT.
                    o.setPaymentIntentId(response.paymentIntentId());
                    orderRepository.save(o);
                }
                default ->
                        log.error("Unknown Stripe status. orderId={}, status={}", orderId, response.status());
            }
            return response;
        });
    }

    private OutboxMessage buildOrderPaidOutbox(Order order) {
        List<SeatDto> seats = order.getSeats();
        SeatDto first = seats.get(0);
        List<Integer> seatNumbers = seats.stream().map(SeatDto::number).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", String.valueOf(order.getId()));
        payload.put("holdId", order.getHoldId());
        payload.put("eventId", order.getEventId());
        payload.put("section", first.section());
        payload.put("row", first.row());
        payload.put("seats", seatNumbers);

        return outboxEntry(RabbitQueue.PAYMENT_EXCHANGE, "order_paid", payload);
    }

    public record OrderExpiredEvent(Long orderId) {
    }

    @RabbitListener(queues = RabbitQueue.ORDER_EXPIRED_QUEUE)
    @Transactional
    public void handleOrderExpired(OrderExpiredEvent event) {
        Long orderId = event.orderId();
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return;

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            log.info("Order {} TTL fired but payment is in flight (AWAITING_PAYMENT), refusing to cancel — reconcile/janitor owns this row", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("Order {} expired but status is already {}, skipping", orderId, order.getStatus());
            return;
        }

        if (order.getPaymentIntentId() != null) {
            log.info("Order {} TTL fired but payment already initiated, preserving order", orderId);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        publishHoldRelease(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.CANCELLED));
        log.info("Order {} cancelled due to expiry", orderId);
    }

    public record PaymentWindowExpiredEvent(Long orderId) {
    }

    @RabbitListener(queues = RabbitQueue.PAYMENT_WINDOW_EXPIRED_QUEUE)
    @Transactional
    public void handlePaymentWindowExpired(PaymentWindowExpiredEvent event) {
        Long orderId = event.orderId();
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return;

        // Only cancel if the order is still in AWAITING_PAYMENT and no PaymentIntent was created.
        // If payment_intent_id is set, Stripe webhook reconciliation owns the row, not us.
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.debug("Payment window message fired but status is {}, skipping. orderId={}",
                    order.getStatus(), orderId);
            return;
        }
        if (order.getPaymentIntentId() != null) {
            log.info("Payment window expired but PaymentIntent exists — Stripe reconciliation owns this. orderId={}", orderId);
            return;
        }
        if (order.getPaymentWindowExpiresAt() == null
                || order.getPaymentWindowExpiresAt().isAfter(LocalDateTime.now())) {
            // Window was extended (e.g., user clicked pay again after a previous attempt's
            // stale message reached us). The new attempt published a new check.
            log.debug("Payment window not yet expired, skipping. orderId={}", orderId);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        publishHoldRelease(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.CANCELLED));
        log.info("Order {} cancelled due to payment-window expiry", orderId);
    }

    @Transactional
    public void abandonOrder(String userId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Ownership check inside the row lock. 404 instead of 403 so an attacker
        // can't probe for valid order IDs they don't own.
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new OrderNotFoundException(orderId);
        }

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            log.info("abandonOrder refused — payment in flight. orderId={}", orderId);
            throw new InvalidOrderStateException("Payment in progress, please wait");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in a state that can be abandoned");
        }

        if (order.getPaymentIntentId() != null) {
            throw new PaymentAlreadyInitiatedException();
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        publishHoldRelease(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.CANCELLED));
        log.info("Order abandoned. orderId={}", orderId);
    }

    private void publishHoldRelease(Order order) {
        List<SeatDto> seats = order.getSeats();
        if (seats == null || seats.isEmpty()) return;

        SeatDto first = seats.get(0);
        List<Integer> seatNumbers = seats.stream().map(SeatDto::number).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", order.getEventId());
        payload.put("section", first.section());
        payload.put("row", first.row());
        payload.put("seats", seatNumbers);
        payload.put("userId", order.getUserId());
        payload.put("holdId", order.getHoldId());
        outboxMessageRepository.save(outboxEntry(RabbitQueue.ORDER_EXCHANGE, RabbitQueue.ORDER_CANCELLED_KEY, payload));
    }

    private BigDecimal calculateAmount(List<SeatDto> seats) {
        return seats.stream()
                .map(SeatDto::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record OrderConfirmationEvent(long orderId) {
    }

    // after inventory deduction succeeded
    @RabbitListener(queues = "q.order.order_confirmation")
    @Transactional
    public void confirmOrder(OrderConfirmationEvent event) {
        long orderId = event.orderId();

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null) return;

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.debug("Order {} already confirmed, skipping", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PROCESSING) {
            log.warn("Order {} in unexpected state {} during confirmation, skipping", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        emailOutboxRepository.save(confirmationEmail(order));
        outboxMessageRepository.save(outboxEntry(
                RabbitQueue.ORDER_EXCHANGE,
                RabbitQueue.ORDER_CONFIRMED_KEY,
                Map.of("orderId", orderId)));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.CONFIRMED));
        log.info("Order {} confirmed", orderId);
    }

    public record InventoryDeductionInitiationEvent(long orderId) {
    }

    public record InventoryDeductionFailedEvent(long orderId) {
    }

    @RabbitListener(queues = RabbitQueue.INVENTORY_DEDUCTION_FAILED_QUEUE)
    @Transactional
    public void handleInventoryDeductionFailed(InventoryDeductionFailedEvent event) {
        long orderId = event.orderId();

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return;

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) return;

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        if (order.getPaymentIntentId() != null) {
            outboxMessageRepository.save(outboxEntry(
                    RabbitQueue.PAYMENT_EXCHANGE,
                    RabbitQueue.REFUND_REQUIRED_KEY,
                    Map.of("orderId", orderId)));
            log.error("Inventory deduction failed, order cancelled, refund queued. orderId={}", orderId);
        } else {
            log.error("Inventory deduction failed, order cancelled. orderId={}", orderId);
        }

        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.CANCELLED));
    }

    // after payment succeeded
    @RabbitListener(queues = "q.order.inventory_deduction_initiation")
    @Transactional
    public void initiateInventoryDeduction(InventoryDeductionInitiationEvent event) {
        Long orderId = event.orderId();

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null) return;

        // This listener now serves only the 3DS webhook path — sync payments are
        // resolved inline by processPayment's reconcile step. AWAITING_PAYMENT is
        // the only valid state; anything else means reconcile already handled it.
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.debug("Order {} in state {}, skipping inventory deduction", orderId, order.getStatus());
            return;
        }

        log.debug("Initiating inventory deduction for order {} (3DS webhook path)", orderId);

        outboxMessageRepository.save(buildOrderPaidOutbox(order));

        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(orderId, OrderStatus.PROCESSING));
    }

    private OutboxMessage outboxEntry(String exchange, String routingKey, Map<String, Object> payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setExchange(exchange);
        msg.setRoutingKey(routingKey);
        msg.setPayload(payload);
        msg.setStatus(OutboxMessageStatus.PENDING);
        return msg;
    }

    private EmailOutboxMessage confirmationEmail(Order order) {
        EmailOutboxMessage msg = new EmailOutboxMessage();
        msg.setToEmail(order.getUserEmail());
        msg.setSubject("Booking Confirmed — Order #" + order.getId());
        msg.setOrderId(order.getId());
        msg.setEventId(order.getEventId());
        msg.setSeats(order.getSeats());
        msg.setAmount(order.getAmount());
        return msg;
    }
}
