package com.example.event.mapper;

import com.example.event.dto.response.ArtistOutDto;
import com.example.event.dto.response.EventOutDto;
import com.example.event.entity.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final VenueMapper venueMapper;
    private final CategoryMapper categoryMapper;
    private final ArtistMapper artistMapper;

    public EventOutDto toResponse(Event event) {
        if (event == null) return null;
        Set<ArtistOutDto> artistResponses = event.getArtists().stream()
                .map(artistMapper::toResponse)
                .collect(Collectors.toSet());

        return EventOutDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .venue(venueMapper.toResponse(event.getVenue()))
                .category(categoryMapper.toResponse(event.getCategory()))
                .organizerId(event.getOrganizerId())
                .artists(artistResponses)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
