package com.example.event.mapper;

import com.example.event.dto.response.ArtistOutDto;
import com.example.event.entity.Artist;
import org.springframework.stereotype.Component;

@Component
public class ArtistMapper {

    public ArtistOutDto toResponse(Artist artist) {
        if (artist == null) return null;
        return ArtistOutDto.builder()
                .id(artist.getId())
                .name(artist.getName())
                .bio(artist.getBio())
                .imageUrl(artist.getImageUrl())
                .genre(artist.getGenre())
                .createdAt(artist.getCreatedAt())
                .updatedAt(artist.getUpdatedAt())
                .build();
    }
}
