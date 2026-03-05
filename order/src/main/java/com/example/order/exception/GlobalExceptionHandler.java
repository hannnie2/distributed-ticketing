package com.example.order.exception;

import com.example.order.constants.ApiResponse;
import com.example.order.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleSeatUnavailable(SeatUnavailableException e) {
        return Result.fail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentFailed(PaymentFailedException e) {
        return Result.fail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleHoldExpired(HoldExpiredException e) {
        return Result.fail(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
