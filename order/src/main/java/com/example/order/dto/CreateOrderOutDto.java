package com.example.order.dto;

import java.time.Instant;

public record CreateOrderOutDto(String holdId, Instant expiresAt) {
}
