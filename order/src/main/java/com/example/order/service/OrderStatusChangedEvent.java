package com.example.order.service;

import com.example.order.constants.OrderStatus;

public record OrderStatusChangedEvent(long orderId, OrderStatus status) {
}
