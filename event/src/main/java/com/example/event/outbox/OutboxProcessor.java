package com.example.event.outbox;

import com.example.event.constants.OutboxStatus;
import com.example.event.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {
    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        outboxMessageRepository.findAllByStatus(OutboxStatus.PENDING).forEach(msg -> {
            try {
                if (msg.getTargetTime() != null) {
                    long ttl = ChronoUnit.MILLIS.between(LocalDateTime.now(), msg.getTargetTime());
                    rabbitTemplate.convertAndSend(msg.getExchange(), msg.getRoutingKey(), msg.getPayload(),
                            m -> {
                                m.getMessageProperties().setExpiration(String.valueOf(Math.max(0, ttl)));
                                return m;
                            });
                } else {
                    rabbitTemplate.convertAndSend(msg.getExchange(), msg.getRoutingKey(), msg.getPayload());
                }
                msg.setStatus(OutboxStatus.PROCESSED);
            } catch (Exception e) {
                log.error("Failed to relay outbox message. exchange={}, routingKey={}",
                        msg.getExchange(), msg.getRoutingKey(), e);
            }
        });
    }
}
