package com.example.user.constants;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
}
