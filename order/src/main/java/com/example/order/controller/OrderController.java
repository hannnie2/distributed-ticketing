package com.example.order.controller;

import com.example.order.constants.OrderStatus;
import com.example.order.dto.CreateOrderInDto;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.order.service.OrderStatusService;
import com.example.order.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;
    private final OrderRepository orderRepository;

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader("x-user-id") String userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CreateOrderInDto createOrderInDto) {
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("X-Idempotency-Key must not be blank");
        }
        return Result.success("Order created",
                orderService.createOrder(userId, idempotencyKey, createOrderInDto));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getOrderStatus(@PathVariable(name = "id") Long orderId) {
        return Result.success("Order status", orderStatusService.getOrderStatus(orderId));
    }

    @GetMapping(value = "/{id}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderStatus(@PathVariable(name = "id") Long orderId) {
        return orderStatusService.subscribe(orderId);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payOrder(@RequestHeader("x-user-id") String userId,
                                      @PathVariable(name = "id") Long orderId,
                                      @RequestBody PayOrderRequest request) {
        return Result.success("Payment processed",
                orderService.payOrder(userId, orderId, request.confirmationTokenId()));
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<?> abandonOrder(@RequestHeader("x-user-id") String userId,
                                          @PathVariable(name = "id") Long orderId) {
        orderService.abandonOrder(userId, orderId);
        return Result.success("Order abandoned", null);
    }

    // Called by inventory's orphan reconciler. Returns the subset of submitted hold IDs
    // whose orders are still in a state that legitimately holds seats.
    @PostMapping("/active-holds")
    public ResponseEntity<?> activeHolds(@RequestBody ActiveHoldsRequest request) {
        if (request.holdIds() == null || request.holdIds().isEmpty()) {
            return Result.success("Active holds", List.of());
        }
        Set<OrderStatus> active = Set.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT,
                OrderStatus.PROCESSING, OrderStatus.CONFIRMED, OrderStatus.FULFILLED);
        List<String> activeIds = orderRepository.findActiveHoldIds(request.holdIds(), active);
        return Result.success("Active holds", activeIds);
    }

    public record ActiveHoldsRequest(List<String> holdIds) {
    }

    public record PayOrderRequest(String confirmationTokenId) {
    }
}
