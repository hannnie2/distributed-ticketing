package com.example.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReleaseHoldInDto(
        @NotNull Integer eventId,
        @NotNull Integer section,
        @NotNull String row,
        @NotEmpty List<Integer> seats
) {
}
