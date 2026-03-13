package com.example.order.service;

import com.example.order.api.EventApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCacheService {

    private static final String KEY_PREFIX = "event:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final EventApi eventApi;
    private final ObjectMapper objectMapper;

    public EventApi.EventDetails getEventDetails(int eventId) {
        String key = KEY_PREFIX + eventId;

        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, EventApi.EventDetails.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached event details for eventId={}", eventId);
            }
        }

        EventApi.EventDetails details = eventApi.getEvent(eventId);

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(details), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache event details for eventId={}", eventId);
        }

        return details;
    }
}
