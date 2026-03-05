package com.example.event.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record VenueOutDto(
        Long id,
        String name,
        String address,
        String city,
        String country,
        Integer capacity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
