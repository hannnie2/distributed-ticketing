package com.example.inventory.outbox;

import com.example.inventory.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final RabbitMessageProcessor rabbitMessageProcessor;

    @Scheduled(fixedDelay = 2000)
    public void relay() {
        List<OutboxMessage> messages = rabbitMessageProcessor.markAsProcessing();
        for (OutboxMessage msg : messages) {
            rabbitMessageProcessor.processOne(msg);
        }
    }
}
