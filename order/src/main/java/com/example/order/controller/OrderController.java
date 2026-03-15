package com.example.order.controller;

import com.example.order.dto.CreateOrderInDto;
import com.example.order.service.OrderService;
import com.example.order.service.OrderStatusService;
import com.example.order.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderInDto createOrderInDto) {
        return Result.success("Order created", orderService.createOrder(createOrderInDto));
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
    public ResponseEntity<?> payOrder(@PathVariable(name = "id") Long orderId,
                                      @RequestBody PayOrderRequest request) {
        return Result.success("Payment processed", orderService.processPayment(orderId, request.confirmationTokenId()));
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<?> abandonOrder(@PathVariable(name = "id") Long orderId) {
        orderService.abandonOrder(orderId);
        return Result.success("Order abandoned", null);
    }

    public record PayOrderRequest(String confirmationTokenId) {
    }
}
