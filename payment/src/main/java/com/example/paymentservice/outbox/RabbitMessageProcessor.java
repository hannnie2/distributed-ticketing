package com.example.paymentservice.outbox;

import com.example.paymentservice.constants.OutboxStatus;
import com.example.paymentservice.entity.OutboxMessage;
import com.example.paymentservice.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMessageProcessor {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public List<OutboxMessage> markAsProcessing() {
        List<OutboxMessage> messages = outboxMessageRepository.findPendingBatch();
        messages.forEach(msg -> msg.setStatus(OutboxStatus.PROCESSING));
        return messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(OutboxMessage message) {
        try {
            rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), message.getPayload());
            message.setStatus(OutboxStatus.PROCESSED);
        } catch (Exception e) {
            message.setStatus(OutboxStatus.PENDING);
            log.error("Failed to relay outbox message. exchange={}, routingKey={}",
                    message.getExchange(), message.getRoutingKey(), e);
        }
        outboxMessageRepository.save(message);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuck() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        outboxMessageRepository.resetStuckProcessing(cutoff);
    }
}
