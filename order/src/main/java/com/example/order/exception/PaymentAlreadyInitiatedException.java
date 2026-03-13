package com.example.order.exception;

public class PaymentAlreadyInitiatedException extends RuntimeException {
    public PaymentAlreadyInitiatedException() {
        super("Payment already initiated for this order");
    }
}
