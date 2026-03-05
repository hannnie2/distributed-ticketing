package com.example.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldSeatsInDto(
        @NotNull Integer eventId,
        @NotEmpty List<SeatDto> seats
) {}
