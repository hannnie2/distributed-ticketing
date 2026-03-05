package com.example.event.dto.response;

import lombok.Builder;

import java.util.Map;

@Builder
public record ApiResponse<T>(boolean success, String message, T data, Map<String, String> errors) {}
