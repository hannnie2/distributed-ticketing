package com.example.order.constants;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}