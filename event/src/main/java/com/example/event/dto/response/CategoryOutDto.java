package com.example.event.dto.response;

import lombok.Builder;

@Builder
public record CategoryOutDto(Long id, String name, String description) {}
