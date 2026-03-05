package com.example.inventory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldSeatsOutDto(
        UUID holdId,
        Instant expiresAt,
        List<SeatPriceDto> seats
) {
}
