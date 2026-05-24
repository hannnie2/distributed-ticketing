package com.example.user.outbox;

import com.example.user.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RabbitOutboxRelay {

    private final RabbitMessageProcessor rabbitMessageProcessor;

    public void relay() {
        List<OutboxMessage> messages = rabbitMessageProcessor.markAsProcessing();
        for (OutboxMessage msg : messages) {
            rabbitMessageProcessor.processOne(msg);
        }
    }
}
