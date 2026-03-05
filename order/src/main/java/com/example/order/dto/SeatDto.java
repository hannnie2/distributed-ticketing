package com.example.order.dto;

import java.math.BigDecimal;

public record SeatDto(int section, String row, int number, BigDecimal price) {
}
