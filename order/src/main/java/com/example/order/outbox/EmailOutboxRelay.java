package com.example.order.outbox;

import com.example.order.entity.EmailOutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailOutboxRelay {

    private final EmailMessageProcessor emailMessageProcessor;

    public void relay() {
        List<EmailOutboxMessage> messages = emailMessageProcessor.markAsProcessing();
        for (EmailOutboxMessage msg : messages) {
            emailMessageProcessor.processOne(msg);
        }
    }
}
