package com.example.inventory.util;

import com.example.inventory.constants.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Result {
    public static <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, message, data)
        );
    }

    public static <T> ResponseEntity<ApiResponse<T>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(false, message, null));
    }

}
