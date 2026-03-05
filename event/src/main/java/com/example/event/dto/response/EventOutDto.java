package com.example.event.dto.response;

import com.example.event.enums.EventStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder
public record EventOutDto(
        Long id,
        String title,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        EventStatus status,
        VenueOutDto venue,
        CategoryOutDto category,
        UUID organizerId,
        Set<ArtistOutDto> artists,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
