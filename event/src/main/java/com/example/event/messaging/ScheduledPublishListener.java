package com.example.event.messaging;

import com.example.event.config.RabbitConfig;
import com.example.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledPublishListener {

    private final EventService eventService;

    public record ScheduledPublishMessage(Long eventId) {}

    @RabbitListener(queues = RabbitConfig.SCHEDULED_PUBLISH_QUEUE)
    public void onScheduledPublish(ScheduledPublishMessage msg) {
        log.debug("Scheduled publish triggered. eventId={}", msg.eventId());
        eventService.publishScheduled(msg.eventId());
    }
}
