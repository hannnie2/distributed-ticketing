package com.example.inventory.constants;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}