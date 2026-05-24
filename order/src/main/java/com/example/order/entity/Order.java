package com.example.order.entity;

import com.example.order.constants.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.order.dto.SeatDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_status_payment_initiated_at",
                        columnList = "status, paymentInitiatedAt"),
                @Index(name = "idx_orders_status_expires_at",
                        columnList = "status, expiresAt"),
                @Index(name = "idx_orders_status_payment_window_expires_at",
                        columnList = "status, paymentWindowExpiresAt")
        },
        uniqueConstraints = {
                // Multiple NULLs are allowed (Postgres default) so existing rows without
                // an idempotency key remain valid. Concurrent retries with the same key
                // fail INSERT here and fall through to the existing-order lookup.
                @UniqueConstraint(name = "ux_orders_user_idempotency_key",
                        columnNames = {"userId", "idempotencyKey"})
        }
)
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int eventId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String paymentIntentId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false, unique = true)
    private String holdId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<SeatDto> seats;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime paymentInitiatedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime paymentWindowExpiresAt;

    // Client-supplied via X-Idempotency-Key header. Same key + same user returns the
    // existing order instead of creating a duplicate.
    @Column(nullable = false)
    private String idempotencyKey;
}

