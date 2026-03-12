package com.example.order.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final RabbitOutboxRelay rabbitOutboxRelay;
    private final EmailOutboxRelay emailOutboxRelay;

    @Scheduled(fixedDelay = 2000)
    public void relayRabbit() {
        rabbitOutboxRelay.relay();
    }

    @Scheduled(fixedDelay = 2000)
    public void relayEmail() {
        emailOutboxRelay.relay();
    }
}
