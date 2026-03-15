package com.example.order.dto;

import java.time.Instant;

public record CreateOrderOutDto(Long orderId, String holdId, Instant expiresAt) {
}
