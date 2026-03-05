package com.example.event.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record EventInDto(
        @NotBlank(message = "Title is required") String title,
        String description,
        @NotNull(message = "Start time is required") @FutureOrPresent(message = "Start time must be present or future") LocalDateTime startTime,
        LocalDateTime endTime,
        Long venueId,
        Long categoryId,
        UUID organizerId,
        Set<Long> artistIds,
        @Future(message = "publishAt must be in the future") LocalDateTime publishAt
) {}
