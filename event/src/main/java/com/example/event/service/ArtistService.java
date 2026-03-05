package com.example.event.service;

import com.example.event.dto.request.ArtistInDto;
import com.example.event.dto.response.ArtistOutDto;
import com.example.event.entity.Artist;
import com.example.event.exception.ResourceNotFoundException;
import com.example.event.mapper.ArtistMapper;
import com.example.event.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final ArtistMapper artistMapper;

    public ArtistOutDto create(ArtistInDto request) {
        Artist artist = Artist.builder()
                .name(request.name())
                .bio(request.bio())
                .imageUrl(request.imageUrl())
                .genre(request.genre())
                .build();
        return artistMapper.toResponse(artistRepository.save(artist));
    }

    public Page<ArtistOutDto> findAll(Pageable pageable) {
        return artistRepository.findAll(pageable).map(artistMapper::toResponse);
    }

    public ArtistOutDto findById(Long id) {
        return artistMapper.toResponse(getOrThrow(id));
    }

    public ArtistOutDto update(Long id, ArtistInDto request) {
        Artist artist = getOrThrow(id);
        artist.setName(request.name());
        artist.setBio(request.bio());
        artist.setImageUrl(request.imageUrl());
        artist.setGenre(request.genre());
        return artistMapper.toResponse(artistRepository.save(artist));
    }

    public void delete(Long id) {
        getOrThrow(id);
        artistRepository.deleteById(id);
    }

    private Artist getOrThrow(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist", id));
    }
}
