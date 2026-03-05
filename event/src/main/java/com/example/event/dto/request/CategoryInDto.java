package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryInDto(
        @NotBlank(message = "Name is required") String name,
        String description
) {}
