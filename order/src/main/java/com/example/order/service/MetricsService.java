package com.example.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class MetricsService {
    private final MeterRegistry registry;

    public void incrementOrderCreated(){
        Counter.builder("orders.created")
                .description("Total orders created")
                .register(registry).increment();
    }

    public void incrementOrderCompleted() {
        Counter.builder("orders.completed")
                .description("Total order completed")
                .register(registry)
                .increment();
    }

    public void incrementOrderFailure(String reason) {
        Counter.builder("orders.failed")
                .tag("reason", reason)
                .description("Total orders failed")
                .register(registry)
                .increment();
    }

    public void incrementSeatHoldRequests(){
        Counter.builder("seat-hold.requested")
                .description("Total failed seat hold requests")
                .register(registry)
                .increment();
    }

    public void incrementSeatHoldFailure(){
        Counter.builder("seat-hold.failed")
                .description("Total failed seat hold requests")
                .register(registry)
                .increment();
    }

    public void incrementSeatHoldSuccess(){
        Counter.builder("seat-hold.success")
                .description("Total successful seat hold requests")
                .register(registry)
                .increment();
    }

    public void incrementPaymentRequests(){
        Counter.builder("payments.requested")
                .description("Total payment requests")
                .register(registry)
                .increment();
    }

    public void incrementPaymentSuccess(){
        Counter.builder("payments.success")
                .description("Total successful payments")
                .register(registry)
                .increment();
    }

    public void incrementPaymentFailure(){
        Counter.builder("payments.failed")
                .description("Total failed payments")
                .register(registry)
                .increment();
    }

    public void incrementEmailRequests(String type){
        Counter.builder("emails.requested")
                .tag("type", type)
                .description("Total email requests")
                .register(registry)
                .increment();
    }

    public void incrementEmailSuccess(){
        Counter.builder("emails.success")
                .description("Total successful emails")
                .register(registry)
                .increment();
    }

    public void incrementEmailFailure(){
        Counter.builder("emails.failed")
                .description("Total failed emails")
                .register(registry)
                .increment();
    }

}
