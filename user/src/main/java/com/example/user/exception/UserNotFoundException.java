package com.example.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String cognitoId) {
        super("User not found: " + cognitoId);
    }
}
