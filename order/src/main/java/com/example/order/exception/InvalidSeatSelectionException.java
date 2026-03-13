package com.example.order.exception;

public class InvalidSeatSelectionException extends RuntimeException {
    public InvalidSeatSelectionException(String message) {
        super(message);
    }
}
