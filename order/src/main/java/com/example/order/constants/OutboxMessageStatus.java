package com.example.order.constants;

public enum OutboxMessageStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
