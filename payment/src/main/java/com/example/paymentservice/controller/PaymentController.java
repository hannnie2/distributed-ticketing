package com.example.paymentservice.controller;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record PaymentIntentRequest(Long orderId, BigDecimal amount, String currency, String confirmationTokenId) {
    }

    @PostMapping("/intent")
    public ResponseEntity<PaymentService.PaymentResult> createPaymentIntent(@RequestBody PaymentIntentRequest request) {
        PaymentService.PaymentResult result = paymentService.createAndConfirm(
                request.orderId(), request.amount(), request.currency(), request.confirmationTokenId());
        if ("failed".equals(result.status())) {
            return ResponseEntity.unprocessableEntity().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        paymentService.handleWebhookEvent(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
