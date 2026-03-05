package com.example.event.controller;

import com.example.event.dto.request.VenueInDto;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.VenueOutDto;
import com.example.event.service.VenueService;
import com.example.event.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<ApiResponse<VenueOutDto>> create(@Valid @RequestBody VenueInDto request) {
        return Result.success(HttpStatus.CREATED, "Created", venueService.create(request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<VenueOutDto>>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return Result.success(HttpStatus.OK, "OK", venueService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VenueOutDto>> findById(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", venueService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VenueOutDto>> update(@PathVariable Long id,
                                                           @Valid @RequestBody VenueInDto request) {
        return Result.success(HttpStatus.OK, "OK", venueService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        venueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
