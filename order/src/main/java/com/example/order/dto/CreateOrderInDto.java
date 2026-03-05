package com.example.order.dto;

import java.util.List;
import java.util.Map;

public record CreateOrderInDto(int eventId, List<Map<String, Object>> seats) {
}
