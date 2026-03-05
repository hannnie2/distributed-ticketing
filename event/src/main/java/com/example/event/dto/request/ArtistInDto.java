package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ArtistInDto(
        @NotBlank(message = "Name is required") String name,
        String bio,
        String imageUrl,
        String genre
) {}
