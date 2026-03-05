package com.example.event.util;

import com.example.event.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public class Result {

    public static <T> ResponseEntity<ApiResponse<T>> success(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(
                ApiResponse.<T>builder()
                        .success(true)
                        .message(message)
                        .data(data)
                        .build()
        );
    }

    public static <T> ResponseEntity<ApiResponse<T>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .build()
        );
    }

    public static <T> ResponseEntity<ApiResponse<T>> fail(HttpStatus status, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(
                ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .errors(errors)
                        .build()
        );
    }
}
