package com.example.order.exception;

public class HoldExpiredException extends RuntimeException {
    public HoldExpiredException(String message) {
        super(message);
    }
}
