package com.example.event.service;

import com.example.event.config.RabbitConfig;
import com.example.event.constants.OutboxStatus;
import com.example.event.dto.request.EventInDto;
import com.example.event.dto.response.EventOutDto;
import com.example.event.entity.Artist;
import com.example.event.entity.Category;
import com.example.event.entity.Event;
import com.example.event.entity.OutboxMessage;
import com.example.event.entity.Venue;
import com.example.event.enums.EventStatus;
import com.example.event.exception.ResourceNotFoundException;
import com.example.event.mapper.EventMapper;
import com.example.event.repository.ArtistRepository;
import com.example.event.repository.CategoryRepository;
import com.example.event.repository.EventRepository;
import com.example.event.repository.OutboxMessageRepository;
import com.example.event.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final CategoryRepository categoryRepository;
    private final ArtistRepository artistRepository;
    private final EventMapper eventMapper;
    private final OutboxMessageRepository outboxMessageRepository;

    public EventOutDto create(EventInDto request) {
        Venue venue = request.venueId() != null
                ? venueRepository.findById(request.venueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue", request.venueId()))
                : null;

        Category category = request.categoryId() != null
                ? categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()))
                : null;

        Set<Artist> artists = resolveArtists(request.artistIds());

        boolean scheduled = request.publishAt() != null;

        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(scheduled ? EventStatus.SCHEDULED : EventStatus.DRAFT)
                .publishAt(request.publishAt())
                .venue(venue)
                .category(category)
                .organizerId(request.organizerId())
                .artists(artists)
                .build();

        Event saved = eventRepository.save(event);

        if (scheduled) {
            schedulePublish(saved.getId(), request.publishAt());
        }

        return eventMapper.toResponse(saved);
    }

    public Page<EventOutDto> findAll(Pageable pageable) {
        return eventRepository.findAllWithVenueAndCategory(pageable)
                .map(eventMapper::toResponse);
    }

    public EventOutDto findById(Long id) {
        return eventMapper.toResponse(getOrThrow(id));
    }

    public EventOutDto update(Long id, EventInDto request) {
        Event event = getOrThrow(id);

        if (request.venueId() != null) {
            Venue venue = venueRepository.findById(request.venueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Venue", request.venueId()));
            event.setVenue(venue);
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
            event.setCategory(category);
        }

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setOrganizerId(request.organizerId());
        event.setArtists(resolveArtists(request.artistIds()));

        return eventMapper.toResponse(eventRepository.save(event));
    }

    public void delete(Long id) {
        getOrThrow(id);
        eventRepository.deleteById(id);
    }

    public EventOutDto publish(Long id) {
        Event event = getOrThrow(id);
        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new IllegalStateException("Event is already published");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cannot publish a cancelled event");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    public EventOutDto cancel(Long id) {
        Event event = getOrThrow(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    /**
     * Called by the scheduled-publish RabbitMQ listener.
     * Idempotent: no-op if the event is not SCHEDULED (e.g. already manually published or cancelled).
     */
    public void publishScheduled(Long id) {
        Event event = getOrThrow(id);
        if (event.getStatus() != EventStatus.SCHEDULED) {
            log.debug("Skipping scheduled publish — event {} has status {}", id, event.getStatus());
            return;
        }
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);
        log.info("Event {} automatically published", id);
    }

    private void schedulePublish(Long eventId, LocalDateTime publishAt) {
        outboxMessageRepository.save(outboxEntry(
                "",
                RabbitConfig.SCHEDULED_PUBLISH_DELAY_QUEUE,
                Map.of("eventId", eventId),
                publishAt));
        log.debug("Scheduled publish outbox entry saved. eventId={}, targetTime={}", eventId, publishAt);

        LocalDateTime warmTime = publishAt.minusMinutes(60);
        if (ChronoUnit.MILLIS.between(LocalDateTime.now(), warmTime) > 0) {
            outboxMessageRepository.save(outboxEntry(
                    "",
                    RabbitConfig.INVENTORY_WARM_CACHE_DELAY_QUEUE,
                    Map.of("eventId", eventId.intValue()),
                    warmTime));
            log.debug("Warm-cache outbox entry saved. eventId={}, targetTime={}", eventId, warmTime);
        } else {
            outboxMessageRepository.save(outboxEntry(
                    "",
                    RabbitConfig.INVENTORY_WARM_CACHE_QUEUE,
                    Map.of("eventId", eventId.intValue()),
                    null));
            log.debug("Warm-cache outbox entry saved for immediate publish. eventId={}", eventId);
        }
    }

    private OutboxMessage outboxEntry(String exchange, String routingKey, Map<String, Object> payload, LocalDateTime targetTime) {
        OutboxMessage msg = new OutboxMessage();
        msg.setExchange(exchange);
        msg.setRoutingKey(routingKey);
        msg.setPayload(payload);
        msg.setTargetTime(targetTime);
        msg.setStatus(OutboxStatus.PENDING);
        return msg;
    }

    private Set<Artist> resolveArtists(Set<Long> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Artist> found = artistRepository.findAllById(artistIds);
        if (found.size() != artistIds.size()) {
            throw new IllegalArgumentException("One or more artist IDs not found");
        }
        return new HashSet<>(found);
    }

    private Event getOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }
}
