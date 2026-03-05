package com.example.order.api;

import com.example.order.exception.PaymentFailedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class PaymentApi {

    private final RestClient restClient;

    public PaymentApi(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public record PaymentRequest(Long orderId, BigDecimal amount, String currency, String confirmationTokenId) {
    }

    public record PaymentResponse(String paymentIntentId, String status, String clientSecret) {
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 4,
            backoff = @Backoff(delay = 200, multiplier = 2))
    public PaymentResponse processPayment(Long orderId, BigDecimal amount, String currency, String confirmationTokenId) {
        return restClient.post()
                .uri("/api/payments/intent")
                .body(new PaymentRequest(orderId, amount, currency, confirmationTokenId))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        (request, response) -> {
                            throw new PaymentFailedException("Payment failed: " + response.getStatusCode());
                        })
                .body(PaymentResponse.class);
    }
}
