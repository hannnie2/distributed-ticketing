package com.example.user.dto.response;

import java.time.LocalDateTime;

public record UserOutDto(
        Long id,
        String cognitoId,
        String email,
        String name,
        LocalDateTime createdAt
) {
}
