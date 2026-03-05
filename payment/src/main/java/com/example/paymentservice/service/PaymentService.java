package com.example.paymentservice.service;

import com.example.paymentservice.config.RabbitConfig;
import com.example.paymentservice.constants.OutboxStatus;
import com.example.paymentservice.constants.PaymentStatus;
import com.example.paymentservice.entity.OutboxMessage;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.OutboxMessageRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public record PaymentResult(String paymentIntentId, String status, String clientSecret) {
    }

    @Transactional
    public PaymentResult createAndConfirm(Long orderId, BigDecimal amount, String currency, String confirmationTokenId) {
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.SUCCEEDED) {
            Payment p = existing.get();
            log.debug("Returning existing succeeded payment for orderId={}", orderId);
            return new PaymentResult(p.getPaymentIntentId(), "succeeded", null);
        }

        Payment payment = existing.orElseGet(Payment::new);
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setConfirmationToken(confirmationTokenId)
                    .setConfirm(true)
                    .setReturnUrl("http://localhost:3000/order-confirmation")
                    .putMetadata("orderId", String.valueOf(orderId))
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey("order-" + orderId)
                    .setMaxNetworkRetries(2)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, requestOptions);

            log.info("PaymentIntent created. id={}, status={}, orderId={}", intent.getId(), intent.getStatus(), orderId);

            PaymentStatus paymentStatus = switch (intent.getStatus()) {
                case "succeeded" -> PaymentStatus.SUCCEEDED;
                case "requires_action", "processing" -> PaymentStatus.PENDING;
                default -> PaymentStatus.FAILED;
            };

            payment.setPaymentIntentId(intent.getId());
            payment.setStatus(paymentStatus);
            paymentRepository.save(payment);

            if (paymentStatus == PaymentStatus.SUCCEEDED) {
                publishPaymentSucceeded(orderId);
            }

            return new PaymentResult(intent.getId(), intent.getStatus(), intent.getClientSecret());

        } catch (ApiConnectionException e) {
            log.error("Network error connecting to Stripe for orderId={}: {}", orderId, e.getMessage());
            throw new RuntimeException("Stripe connection error, please retry", e);
        } catch (StripeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.error("Stripe payment failed for orderId={}: {}", orderId, e.getMessage());
            return new PaymentResult(null, "failed", null);
        }
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

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.debug("Payment already marked succeeded, skipping. paymentIntentId={}", intent.getId());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        publishPaymentSucceeded(payment.getOrderId());
        log.info("Order confirmed via webhook. orderId={}, paymentIntentId={}", payment.getOrderId(), intent.getId());
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
