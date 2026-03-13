package com.example.order.api;

import com.example.order.constants.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Component
@Slf4j
public class EventApi {

    private final RestClient restClient;

    public EventApi(@Qualifier("eventRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public record EventDetails(String title, LocalDateTime startTime, String venueName, String venueCity, String venueAddress) {}

    private record VenueData(String name, String city, String address) {}
    private record EventData(String title, LocalDateTime startTime, VenueData venue) {}

    public EventDetails getEvent(int eventId) {
        ApiResponse<EventData> response = restClient.get()
                .uri("api/events/{id}", eventId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        EventData data = response.data();
        return new EventDetails(data.title(), data.startTime(), data.venue().name(), data.venue().city(), data.venue().address());
    }
}
