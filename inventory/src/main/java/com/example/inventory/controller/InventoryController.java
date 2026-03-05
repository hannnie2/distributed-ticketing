package com.example.inventory.controller;

import com.example.inventory.dto.HoldSeatsInDto;
import com.example.inventory.service.InventoryService;
import com.example.inventory.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping("/holds")
    public ResponseEntity<?> hold(@Valid @RequestBody HoldSeatsInDto holdSeatsInDto) {
        return Result.success("Seats held", inventoryService.holdSeats(holdSeatsInDto));
    }

    @GetMapping("/holds/{holdId}")
    public ResponseEntity<?> checkHold(@PathVariable String holdId) {
        if (!inventoryService.isHoldActive(holdId)) {
            return Result.fail(HttpStatus.GONE, "Hold has expired");
        }
        return Result.success("Hold is active", null);
    }

    @PostMapping("/cache/warm/{eventId}")
    public ResponseEntity<?> warmCache(@PathVariable int eventId) {
        log.info("Manual cache warm triggered. eventId={}", eventId);
        inventoryService.loadInventory(eventId);
        return Result.success("Cache warmed for eventId=" + eventId, null);
    }

}
