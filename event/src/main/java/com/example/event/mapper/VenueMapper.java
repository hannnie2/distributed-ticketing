package com.example.event.mapper;

import com.example.event.dto.response.VenueOutDto;
import com.example.event.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    public VenueOutDto toResponse(Venue venue) {
        if (venue == null) return null;
        return VenueOutDto.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .country(venue.getCountry())
                .capacity(venue.getCapacity())
                .createdAt(venue.getCreatedAt())
                .updatedAt(venue.getUpdatedAt())
                .build();
    }
}
