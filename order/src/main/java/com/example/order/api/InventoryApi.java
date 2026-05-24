package com.example.order.api;

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

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class InventoryApi {
    private final RestClient restClient;

    public InventoryApi(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    private record InventoryHoldRequest(int eventId, String idempotencyKey, List<Map<String, Object>> seats) {
    }

    public record InventoryHoldResponse(String holdId, List<SeatDto> seats) {
    }

    private record ApiResponse<T>(boolean success, String message, T data) {
    }

    // Synchronous best-effort release used by createOrder's catch-block to compensate
    // for a successful hold whose order INSERT failed. Inventory's orphan reconciler
    // is the safety net if this also fails.
    public void releaseHold(int eventId, int section, String row, String holdId, List<Integer> seatNumbers) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("eventId", eventId);
        body.put("section", section);
        body.put("row", row);
        body.put("holdId", holdId);
        body.put("seats", seatNumbers);
        restClient.post()
                .uri("api/v1/holds/{holdId}/release", holdId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // idempotencyKey must be stable across retries: generate it in the caller, before
    // invoking this method, so Spring Retry replays carry the same value and inventory
    // returns the existing hold instead of creating a parallel one.
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            maxAttempts = 4,
            backoff = @Backoff(
                    delay = 200,
                    multiplier = 2
            ))
    public InventoryHoldResponse holdSeats(int eventId, List<Map<String, Object>> seats,
                                           String userId, String idempotencyKey) {
        InventoryHoldRequest req = new InventoryHoldRequest(eventId, idempotencyKey, seats);

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
