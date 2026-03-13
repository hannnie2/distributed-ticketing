package com.example.order.outbox;

import com.example.order.api.EventApi;
import com.example.order.constants.OutboxMessageStatus;
import com.example.order.dto.SeatDto;
import com.example.order.entity.EmailOutboxMessage;
import com.example.order.repository.EmailOutboxRepository;
import com.example.order.service.EmailService;
import com.example.order.service.EventCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailMessageProcessor {

    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy 'at' h:mm a");

    private final EmailOutboxRepository emailOutboxRepository;
    private final EventCacheService eventCacheService;
    private final EmailService emailService;

    @Transactional
    public List<EmailOutboxMessage> markAsProcessing() {
        List<EmailOutboxMessage> messages = emailOutboxRepository.findPendingBatch(BATCH_SIZE);
        messages.forEach(msg -> msg.setStatus(OutboxMessageStatus.PROCESSING));
        return messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(EmailOutboxMessage message) {
        try {
            EventApi.EventDetails event = eventCacheService.getEventDetails(message.getEventId());

            String html = buildHtml(message, event);

            emailService.send(message.getToEmail(), message.getSubject(), html, String.valueOf(message.getId()));
            message.setStatus(OutboxMessageStatus.PROCESSED);
        } catch (Exception e) {
            message.setRetryCount(message.getRetryCount() + 1);
            message.setStatus(message.getRetryCount() >= MAX_RETRIES
                    ? OutboxMessageStatus.FAILED
                    : OutboxMessageStatus.PENDING);
            log.error("Failed to send confirmation email for orderId={} (attempt {}): {}",
                    message.getOrderId(), message.getRetryCount(), e.getMessage());
        }
        emailOutboxRepository.save(message);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuck() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        emailOutboxRepository.resetStuckProcessing(cutoff);
    }

    private String buildHtml(EmailOutboxMessage message, EventApi.EventDetails event) {
        StringBuilder rows = new StringBuilder();
        for (SeatDto seat : message.getSeats()) {
            rows.append(String.format(
                    "<tr><td>Section %d</td><td>Row %s</td><td>Seat %d</td><td>$%.2f</td></tr>",
                    seat.section(), seat.row(), seat.number(), seat.price()));
        }

        return """
                <html><body style="font-family:sans-serif;color:#222">
                  <h2>Your booking is confirmed!</h2>
                  <table style="margin-bottom:16px">
                    <tr><td><strong>Event</strong></td><td>%s</td></tr>
                    <tr><td><strong>Date</strong></td><td>%s</td></tr>
                    <tr><td><strong>Venue</strong></td><td>%s, %s</td></tr>
                    <tr><td><strong>Address</strong></td><td>%s</td></tr>
                  </table>
                  <p>Order <strong>#%d</strong></p>
                  <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse">
                    <thead><tr><th>Section</th><th>Row</th><th>Seat</th><th>Price</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                  <p style="margin-top:16px">Total: <strong>$%.2f</strong></p>
                  <p>Thank you for your purchase!</p>
                </body></html>
                """.formatted(
                event.title(),
                event.startTime().format(DATE_FMT),
                event.venueName(), event.venueCity(),
                event.venueAddress(),
                message.getOrderId(),
                rows,
                message.getAmount());
    }
}
