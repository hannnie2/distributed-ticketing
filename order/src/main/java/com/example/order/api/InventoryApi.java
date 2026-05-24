package com.example.order.api;

import com.example.order.exception.HoldExpiredException;
import com.example.order.exception.SeatUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.example.order.dto.SeatDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class InventoryApi {
    private final RestClient restClient;

    public InventoryApi(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    private record InventoryHoldRequest(int eventId, List<Map<String, Object>> seats) {
    }

    public record InventoryHoldResponse(String holdId, Instant expiresAt, List<SeatDto> seats) {
    }

    private record ApiResponse<T>(boolean success, String message, T data) {
    }

    public void isHoldActive(String holdId) {
        restClient.get()
                .uri("api/v1/holds/{holdId}", holdId)
                .retrieve()
                .onStatus(status -> status.value() == 410,
                        (request, response) -> {
                            throw new HoldExpiredException("Your seat hold has expired. Please start a new order.");
                        })
                .toBodilessEntity();
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 4,
            backoff = @Backoff(
                    delay = 200,
                    multiplier = 2
            ))
    public InventoryHoldResponse holdSeats(int eventId, List<Map<String, Object>> seats, String userId) {
        InventoryHoldRequest req = new InventoryHoldRequest(eventId, seats);

        ApiResponse<InventoryHoldResponse> response = restClient.post()
                .uri("api/v1/holds")
                .header("x-user-id", userId)
                .body(req)
                .retrieve()
                .onStatus(status -> status.value() == 409,
                        (request, resp) -> {
                            throw new SeatUnavailableException("Requested seats are no longer available");
                        })
                .body(new ParameterizedTypeReference<>() {
                });

        return response.data();
    }
}
