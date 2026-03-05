package com.example.inventory.exception;

import com.example.inventory.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class Handler {

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<?> handleSeatsUnavailableException(SeatUnavailableException e) {
        return Result.fail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
