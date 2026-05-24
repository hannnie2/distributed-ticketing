package com.example.user.controller;

import com.example.user.dto.request.UpdateProfileInDto;
import com.example.user.exception.MissingUserHeaderException;
import com.example.user.service.UserService;
import com.example.user.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // AWS API Gateway maps Cognito JWT claims to these headers before forwarding:
    //   x-user-id    <- $context.authorizer.claims.sub
    //   x-user-email <- $context.authorizer.claims.email
    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            @RequestHeader(value = "x-user-id", required = false) String cognitoId,
            @RequestHeader(value = "x-user-email", required = false) String email) {
        validateHeaders(cognitoId, email);
        return Result.success("User profile", userService.getOrCreate(cognitoId, email));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @RequestHeader(value = "x-user-id", required = false) String cognitoId,
            @RequestHeader(value = "x-user-email", required = false) String email,
            @Valid @RequestBody UpdateProfileInDto dto) {
        validateHeaders(cognitoId, email);
        return Result.success("Profile updated", userService.updateProfile(cognitoId, dto));
    }

    private void validateHeaders(String cognitoId, String email) {
        if (cognitoId == null || cognitoId.isBlank() || email == null || email.isBlank()) {
            throw new MissingUserHeaderException();
        }
    }
}
