package com.example.event.service;

import com.example.event.dto.request.VenueInDto;
import com.example.event.dto.response.VenueOutDto;
import com.example.event.entity.Venue;
import com.example.event.exception.ResourceNotFoundException;
import com.example.event.mapper.VenueMapper;
import com.example.event.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    public VenueOutDto create(VenueInDto request) {
        Venue venue = Venue.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .country(request.country())
                .capacity(request.capacity())
                .build();
        return venueMapper.toResponse(venueRepository.save(venue));
    }

    public Page<VenueOutDto> findAll(Pageable pageable) {
        return venueRepository.findAll(pageable).map(venueMapper::toResponse);
    }

    public VenueOutDto findById(Long id) {
        return venueMapper.toResponse(getOrThrow(id));
    }

    public VenueOutDto update(Long id, VenueInDto request) {
        Venue venue = getOrThrow(id);
        venue.setName(request.name());
        venue.setAddress(request.address());
        venue.setCity(request.city());
        venue.setCountry(request.country());
        venue.setCapacity(request.capacity());
        return venueMapper.toResponse(venueRepository.save(venue));
    }

    public void delete(Long id) {
        getOrThrow(id);
        venueRepository.deleteById(id);
    }

    private Venue getOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", id));
    }
}
