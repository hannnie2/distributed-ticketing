package com.example.inventory.dto;

import java.util.List;
import java.util.UUID;

public record HoldSeatsOutDto(
        UUID holdId,
        List<SeatPriceDto> seats
) {
}
