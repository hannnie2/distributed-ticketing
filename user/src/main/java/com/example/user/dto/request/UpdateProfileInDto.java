package com.example.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileInDto(
        @NotBlank @Size(max = 100) String name
) {
}
