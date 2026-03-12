package com.example.order.outbox;

import com.example.order.constants.OutboxMessageStatus;
import com.example.order.entity.EmailOutboxMessage;
import com.example.order.repository.EmailOutboxRepository;
import com.example.order.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailMessageProcessor {

    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE = 100;

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailService emailService;

    @Transactional
    public List<EmailOutboxMessage> markAsProcessing() {
        List<EmailOutboxMessage> messages = emailOutboxRepository.findPendingBatch(BATCH_SIZE);
        messages.forEach(msg -> msg.setStatus(OutboxMessageStatus.PROCESSING));
        return messages;
}

    @Transactional()
    public void processOne(EmailOutboxMessage message) {
        try {
            emailService.send(message.getToEmail(), message.getSubject(), message.getHtmlBody(), String.valueOf(message.getId()));
            message.setStatus(OutboxMessageStatus.PROCESSED);
        } catch (Exception e) {
            message.setRetryCount(message.getRetryCount() + 1);
            message.setStatus(message.getRetryCount() >= MAX_RETRIES
                    ? OutboxMessageStatus.FAILED
                    : OutboxMessageStatus.PENDING);
            log.error("Failed to send email to {} (attempt {}): {}",
                    message.getToEmail(), message.getRetryCount(), e.getMessage());
        }
        emailOutboxRepository.save(message);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuck() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        emailOutboxRepository.resetStuckProcessing(cutoff);
    }
}
