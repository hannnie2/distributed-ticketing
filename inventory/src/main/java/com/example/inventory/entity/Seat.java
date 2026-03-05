package com.example.inventory.entity;

import com.example.inventory.constants.SeatStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "seats",
        indexes = {
                @Index(name = "idx_seat_identity", columnList = "event_id, section, row, number"),
                @Index(name = "idx_seat_event_status", columnList = "event_id, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_seat_identity", columnNames = {"event_id", "section", "row", "number"})
        }
)
@Data
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String row;

    @Column(nullable = false)
    private int section;

    @Column(nullable = false)
    private int eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private String orderId;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;
}


