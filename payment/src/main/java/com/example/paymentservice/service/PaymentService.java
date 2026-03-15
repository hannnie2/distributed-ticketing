package com.example.paymentservice.service;

import com.example.paymentservice.config.RabbitConfig;
import com.example.paymentservice.constants.OutboxStatus;
import com.example.paymentservice.constants.PaymentStatus;
import com.example.paymentservice.entity.OutboxMessage;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.OutboxMessageRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.stripe.exception.*;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate txTemplate;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public record PaymentResult(String paymentIntentId, String status, String clientSecret) {
    }

    public PaymentResult createAndConfirm(Long orderId, BigDecimal amount, String currency, String confirmationTokenId) {
        Payment payment = txTemplate.execute(status -> {
            Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
            if (existing.isPresent()) return existing.get();

            Payment p = new Payment();
            p.setOrderId(orderId);
            p.setAmount(amount);
            p.setCurrency(currency);
            p.setStatus(PaymentStatus.PENDING);
            try {
                paymentRepository.saveAndFlush(p);
            } catch (DataIntegrityViolationException e) {
                return paymentRepository.findByOrderId(orderId).get();
            }
            return p;
        });

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.debug("Returning existing succeeded payment for orderId={}", orderId);
            return new PaymentResult(payment.getPaymentIntentId(), "succeeded", null);
        }

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                )
                .setAmount(amountInCents)
                .setCurrency(currency)
                .setConfirmationToken(confirmationTokenId)
                .setConfirm(true)
                .setReturnUrl("http://localhost:3000/order-confirmation")
                .putMetadata("orderId", String.valueOf(orderId))
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("order-" + orderId + "-" + confirmationTokenId)
                .setMaxNetworkRetries(2)
                .build();

        PaymentIntent intent;
        try {
            intent = PaymentIntent.create(params, requestOptions);
        } catch (CardException e) {
            log.error("Stripe card error for orderId={}: {}", orderId, e.getMessage());
            return new PaymentResult(null, "failed", null);
        } catch (ApiConnectionException | RateLimitException e) {
            log.error("Transient Stripe error for orderId={}: {}", orderId, e.getMessage());
            throw new RuntimeException("Stripe temporarily unavailable, please retry", e);
        } catch (ApiException e) {
            log.error("Stripe internal error for orderId={}: {}", orderId, e.getMessage());
            throw new RuntimeException("Stripe internal error, please retry", e);
        } catch (StripeException e) {
            // InvalidRequestException, AuthenticationException, etc. not retryable
            log.error("Non-retryable Stripe error for orderId={}: {}", orderId, e.getMessage());
            return new PaymentResult(null, "failed", null);
        } catch (Exception e) {
            log.error("Unexpected error for orderId={}: {}", orderId, e.getMessage());
            throw e;
        }

        log.info("PaymentIntent created. id={}, status={}, orderId={}", intent.getId(), intent.getStatus(), orderId);

        PaymentStatus paymentStatus = switch (intent.getStatus()) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            default -> throw new IllegalStateException(
                    "Unexpected PaymentIntent status: " + intent.getStatus() + " for orderId=" + orderId);
        };

        txTemplate.executeWithoutResult(status -> {
            Payment p = paymentRepository.findByOrderId(orderId).orElseThrow();
            p.setPaymentIntentId(intent.getId());
            p.setStatus(paymentStatus);
            paymentRepository.save(p);

            if (paymentStatus == PaymentStatus.SUCCEEDED) {
                publishPaymentSucceeded(orderId);
            }
        });

        return new PaymentResult(intent.getId(), intent.getStatus(), intent.getClientSecret());
    }

    @Transactional
    public void handleWebhookEvent(byte[] payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(new String(payload), sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new RuntimeException("Invalid webhook signature", e);
        }

        log.debug("Received Stripe webhook event: {}", event.getType());

        if (!"payment_intent.succeeded".equals(event.getType())) {
            return;
        }

        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("Failed to deserialize Stripe event data"));

        PaymentIntent intent = (PaymentIntent) stripeObject;

        Payment payment = paymentRepository.findByPaymentIntentId(intent.getId()).orElse(null);
        if (payment == null) {
            log.warn("No payment record found for paymentIntentId={}", intent.getId());
            return;
        }

        if (payment.getStatus() != PaymentStatus.REQUIRES_ACTION) {
            log.debug("Payment for orderId={} is in status {}, skipping webhook. paymentIntentId={}",
                    payment.getOrderId(), payment.getStatus(), intent.getId());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        publishPaymentSucceeded(payment.getOrderId());
        log.info("Required action completed, payment succeeded via webhook. orderId={}, paymentIntentId={}",
                payment.getOrderId(), intent.getId());
    }

    private void publishPaymentSucceeded(Long orderId) {
        OutboxMessage msg = new OutboxMessage();
        msg.setExchange(RabbitConfig.PAYMENT_EXCHANGE);
        msg.setRoutingKey(RabbitConfig.PAYMENT_SUCCEEDED_KEY);
        msg.setPayload(Map.of("orderId", orderId));
        msg.setStatus(OutboxStatus.PENDING);
        outboxMessageRepository.save(msg);
        log.debug("payment_succeeded queued in outbox for orderId={}", orderId);
    }
}
