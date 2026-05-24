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

    public CreateOrderOutDto createOrder(CreateOrderInDto createOrderInDto) {
        long distinctRows = createOrderInDto.seats().stream()
                .map(s -> s.get("section") + ":" + s.get("row"))
                .distinct()
                .count();
        if (distinctRows > 1) {
            throw new InvalidSeatSelectionException("All seats in an order must belong to the same section and row");
        }

        InventoryApi.InventoryHoldResponse response = inventoryApi.holdSeats(
                createOrderInDto.eventId(), createOrderInDto.seats());

        Order createdOrder;
        try {
            createdOrder = transactionTemplate.execute(status -> {
                Order order = new Order();
                order.setEventId(createOrderInDto.eventId());
                order.setUserEmail(createOrderInDto.userEmail());
                order.setSeats(response.seats());
                order.setAmount(calculateAmount(response.seats()));
                order.setStatus(OrderStatus.PENDING);
                order.setHoldId(response.holdId());

                orderRepository.save(order);
                log.info("Order created. orderId={}, eventId={}", order.getId(), order.getEventId());

                outboxMessageRepository.save(outboxEntry(
                        RabbitQueue.ORDER_EXCHANGE,
                        RabbitQueue.ORDER_PENDING_CREATED_KEY,
                        Map.of("orderId", order.getId())));

                return order;
            });
        } catch (Exception e) {
            log.error("Order DB write failed. holdId={} will expire naturally", response.holdId(), e);
            throw new RuntimeException("Order creation failed, please retry", e);
        }

        if (createdOrder == null) {
            log.error("Order transaction was rolled back. holdId={} will expire naturally", response.holdId());
            throw new RuntimeException("Order creation failed, please retry");
        }

        return new CreateOrderOutDto(createdOrder.getId(), response.holdId(), response.expiresAt());
    }

    public PaymentApi.PaymentResponse processPayment(Long orderId, String confirmationTokenId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in pending state");
        }

        if (order.getPaymentIntentId() != null) {
            throw new PaymentAlreadyInitiatedException();
        }

        inventoryApi.isHoldActive(order.getHoldId());

        PaymentApi.PaymentResponse response = paymentApi.processPayment(
                orderId, order.getAmount(), "cad", confirmationTokenId);

        if ("failed".equals(response.status())) {
            return response;
        }

        int updated = orderRepository.savePaymentResult(orderId, response.paymentIntentId(), OrderStatus.PENDING);
        if (updated == 0) {
            log.warn("Order {} payment result already saved by a concurrent request, skipping", orderId);
        }

        return response;
    }

    public record OrderExpiredEvent(Long orderId) {
    }

    @RabbitListener(queues = RabbitQueue.ORDER_EXPIRED_QUEUE)
    @Transactional
    public void handleOrderExpired(OrderExpiredEvent event) {
        Long orderId = event.orderId();
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return;

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

    @Transactional
    public void abandonOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

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
        payload.put("userId", "test_user");
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

        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("Order {} is no longer PENDING (status={}), skipping inventory deduction", orderId, order.getStatus());
            return;
        }

        log.debug("Initiating inventory deduction for order {}", orderId);

        // Orders are guaranteed to be single section:row (enforced at creation)
        List<SeatDto> seats = order.getSeats();
        SeatDto first = seats.get(0);
        List<Integer> seatNumbers = seats.stream().map(SeatDto::number).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", String.valueOf(orderId));
        payload.put("eventId", order.getEventId());
        payload.put("section", first.section());
        payload.put("row", first.row());
        payload.put("seats", seatNumbers);

        outboxMessageRepository.save(outboxEntry(RabbitQueue.PAYMENT_EXCHANGE, "order_paid", payload));

        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);
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
