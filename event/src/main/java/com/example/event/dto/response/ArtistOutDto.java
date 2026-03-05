package com.example.event.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ArtistOutDto(
        Long id,
        String name,
        String bio,
        String imageUrl,
        String genre,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
