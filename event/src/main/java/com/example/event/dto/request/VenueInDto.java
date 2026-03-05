package com.example.event.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VenueInDto(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Address is required") String address,
        @NotBlank(message = "City is required") String city,
        @NotBlank(message = "Country is required") String country,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") Integer capacity
) {}
