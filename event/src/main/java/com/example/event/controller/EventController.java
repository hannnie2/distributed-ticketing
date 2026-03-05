package com.example.event.controller;

import com.example.event.dto.request.EventInDto;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.EventOutDto;
import com.example.event.service.EventService;
import com.example.event.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventOutDto>> create(@Valid @RequestBody EventInDto request) {
        return Result.success(HttpStatus.CREATED, "Created", eventService.create(request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventOutDto>>> findAll(
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return Result.success(HttpStatus.OK, "OK", eventService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventOutDto>> findById(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", eventService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventOutDto>> update(@PathVariable Long id,
                                                           @Valid @RequestBody EventInDto request) {
        return Result.success(HttpStatus.OK, "OK", eventService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<EventOutDto>> publish(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", eventService.publish(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<EventOutDto>> cancel(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", eventService.cancel(id));
    }
}
