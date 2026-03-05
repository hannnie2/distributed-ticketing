package com.example.event.controller;

import com.example.event.dto.request.ArtistInDto;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.ArtistOutDto;
import com.example.event.service.ArtistService;
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
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @PostMapping
    public ResponseEntity<ApiResponse<ArtistOutDto>> create(@Valid @RequestBody ArtistInDto request) {
        return Result.success(HttpStatus.CREATED, "Created", artistService.create(request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ArtistOutDto>>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return Result.success(HttpStatus.OK, "OK", artistService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistOutDto>> findById(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", artistService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistOutDto>> update(@PathVariable Long id,
                                                            @Valid @RequestBody ArtistInDto request) {
        return Result.success(HttpStatus.OK, "OK", artistService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        artistService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
