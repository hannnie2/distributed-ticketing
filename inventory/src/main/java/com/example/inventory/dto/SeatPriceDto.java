package com.example.inventory.dto;

import java.math.BigDecimal;

public record SeatPriceDto(int section, String row, int number, BigDecimal price) {
}
