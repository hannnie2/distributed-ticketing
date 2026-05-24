package com.example.user.exception;

public class MissingUserHeaderException extends RuntimeException {
    public MissingUserHeaderException() {
        super("Missing required identity headers from API Gateway");
    }
}
