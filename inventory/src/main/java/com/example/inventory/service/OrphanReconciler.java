package com.example.inventory.service;

import com.example.inventory.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Sweeps Redis for hold hashes that no longer correspond to an active order in the order DB.
// Catches the gap when createOrder's catch-block compensation also fails, or any other path
// that leaves an orphan hold in Redis.
//
// Runs hourly. Skip the first ~30 minutes of a hold's life to avoid racing live createOrder /
// payment flows whose order rows might not be visible yet via the order service's query.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrphanReconciler {

    private static final Pattern HOLD_KEY = Pattern.compile(
            "\\{e:(\\d+):s:(\\d+):r:([^}]+)\\}:hold:(.+)");
    private static final int BATCH_SIZE = 200;

    private final RedisTemplate<String, String> redisTemplate;
    private final InventoryService inventoryService;
    private final RestClient orderRestClient;

    private record ApiResponse<T>(boolean success, String message, T data) {}

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void reconcile() {
        log.info("Orphan reconciler starting");

        List<HoldRef> batch = new ArrayList<>();
        int totalScanned = 0;
        int totalReleased = 0;

        ScanOptions opts = ScanOptions.scanOptions()
                .match("{e:*:s:*:r:*}:hold:*")
                .count(BATCH_SIZE)
                .build();

        try (var cursor = redisTemplate.scan(opts)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                HoldRef ref = parse(key);
                if (ref == null) continue;
                totalScanned++;
                batch.add(ref);
                if (batch.size() >= BATCH_SIZE) {
                    totalReleased += processBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                totalReleased += processBatch(batch);
            }
        } catch (Exception e) {
            log.error("Orphan reconciler aborted", e);
            return;
        }

        log.info("Orphan reconciler done. scanned={}, released={}", totalScanned, totalReleased);
    }

    private int processBatch(List<HoldRef> batch) {
        List<String> holdIds = batch.stream().map(HoldRef::holdId).toList();

        Set<String> activeHoldIds;
        try {
            ApiResponse<List<String>> resp = orderRestClient.post()
                    .uri("/api/orders/active-holds")
                    .body(Map.of("holdIds", holdIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            activeHoldIds = resp != null && resp.data() != null
                    ? new HashSet<>(resp.data())
                    : Set.of();
        } catch (Exception e) {
            log.error("Active-holds lookup failed; skipping this batch", e);
            return 0;
        }

        int released = 0;
        for (HoldRef ref : batch) {
            if (activeHoldIds.contains(ref.holdId())) continue;
            try {
                List<Integer> seats = readSeats(ref);
                if (seats == null) continue;
                inventoryService.releaseHoldDirect(ref.eventId(), ref.section(), ref.row(),
                        ref.holdId(), seats);
                released++;
                log.warn("Released orphan hold. holdId={}, eventId={}, section={}, row={}",
                        ref.holdId(), ref.eventId(), ref.section(), ref.row());
            } catch (Exception e) {
                log.error("Failed to release orphan hold. holdId={}", ref.holdId(), e);
            }
        }
        return released;
    }

    private List<Integer> readSeats(HoldRef ref) {
        String key = RedisKeys.hold(ref.eventId(), ref.section(), ref.row(), ref.holdId());
        Object raw = redisTemplate.opsForHash().get(key, "seats");
        if (raw == null) return null;
        String csv = raw.toString();
        if (csv.isEmpty()) return List.of();
        List<Integer> seats = new ArrayList<>();
        for (String off : csv.split(",")) {
            // Hash stores 0-based offsets; release API expects 1-based seat numbers.
            seats.add(Integer.parseInt(off.trim()) + 1);
        }
        return seats;
    }

    private HoldRef parse(String key) {
        Matcher m = HOLD_KEY.matcher(key);
        if (!m.matches()) return null;
        return new HoldRef(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                m.group(3),
                m.group(4));
    }

    private record HoldRef(int eventId, int section, String row, String holdId) {}
}
