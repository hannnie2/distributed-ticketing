package com.example.user.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final RabbitOutboxRelay rabbitOutboxRelay;

    @Scheduled(fixedDelay = 2000)
    public void relayRabbit() {
        rabbitOutboxRelay.relay();
    }
}
