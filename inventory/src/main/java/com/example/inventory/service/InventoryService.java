package com.example.inventory.service;

import com.example.inventory.config.RabbitConfig;
import com.example.inventory.constants.OutboxStatus;
import com.example.inventory.constants.SeatStatus;
import com.example.inventory.dto.HoldSeatsInDto;
import com.example.inventory.dto.HoldSeatsOutDto;
import com.example.inventory.dto.SeatDto;
import com.example.inventory.dto.SeatPriceDto;
import com.example.inventory.entity.OutboxMessage;
import com.example.inventory.entity.Seat;
import com.example.inventory.exception.SeatUnavailableException;
import com.example.inventory.repository.OutboxMessageRepository;
import com.example.inventory.repository.SeatRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final RedisTemplate<String, String> redisTemplate;
    private final SeatRepository seatRepository;
    private final RedisScript<Boolean> holdSeatsScript;
    private final RedisScript<Boolean> releaseSeatsScript;
    private final OutboxMessageRepository outboxMessageRepository;

    public HoldSeatsOutDto holdSeats(HoldSeatsInDto holdSeatsInDto) {
        int eventId = holdSeatsInDto.eventId();
        List<SeatDto> seats = holdSeatsInDto.seats();
        SeatDto first = seats.get(0);
        String sectionRow = first.section() + ":" + first.row();
        String rowKey = "seats:" + eventId + ":" + sectionRow;

        log.debug("Holding seats. eventId={}, section={}, row={}", eventId, first.section(), first.row());

        List<String> offsets = seats.stream()
                .map(s -> String.valueOf(s.number() - 1))
                .distinct()
                .toList();

        Boolean ok = redisTemplate.execute(
                holdSeatsScript,
                List.of(rowKey, rowKey + ":cap"),
                offsets.toArray(new String[0])
        );

        if (!ok) {
            log.debug("Seat hold failed. eventId={}, section={}, row={}, seats={}",
                    eventId, first.section(), first.row(), offsets);
            throw new SeatUnavailableException("Seats unavailable");
        }

        List<String> seatNumbers = seats.stream()
                .map(s -> String.valueOf(s.number()))
                .toList();

        String pricesKey = "prices:" + eventId + ":" + sectionRow;
        List<Object> priceValues = redisTemplate.opsForHash().multiGet(pricesKey, new ArrayList<>(seatNumbers));

        List<SeatPriceDto> seatPrices = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            SeatDto seat = seats.get(i);
            String priceStr = (String) priceValues.get(i);
            BigDecimal price = priceStr != null ? new BigDecimal(priceStr) : null;
            seatPrices.add(new SeatPriceDto(first.section(), first.row(), seat.number(), price));
        }

        UUID holdId = UUID.randomUUID();
        String key = "hold:" + holdId;

        Map<String, String> hash = Map.of(
                "userId", "test_user",
                "eventId", String.valueOf(eventId),
                "row", sectionRow,
                "seats", String.join(",", offsets));

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, Duration.ofMinutes(10));

        return new HoldSeatsOutDto(
                holdId,
                Instant.now().plus(Duration.ofMinutes(10)),
                seatPrices
        );
    }

    private record HoldRecord(String holdId, String userId, int eventId, int section, String row, List<Integer> seats) {
    }

//    @Nullable
//    private HoldRecord verifyHold(String holdId, String userId) {
//        String key = "hold:" + holdId;
//
//        List<Object> v = redisTemplate.opsForHash().multiGet(
//                key, List.of("userId", "eventId", "row", "seats")
//        );
//
//        if (v == null || v.size() < 4) return null;
//
//        String storedUserId = (String) v.get(0);
//        String eventIdStr = (String) v.get(1);
//        String sectionRowStr = (String) v.get(2);
//        String seatsStr = (String) v.get(3);
//
//        if (storedUserId == null || eventIdStr == null || sectionRowStr == null || seatsStr == null) return null;
//
//        if (!userId.equals(storedUserId)) return null;
//
//        String[] sectionRow = sectionRowStr.split(":");
//
//        if (sectionRow.length != 2) return null;
//
//        String sectionStr = sectionRow[0].trim();
//        String row = sectionRow[1].trim();
//        if (sectionStr.isEmpty() || row.isEmpty()) return null;
//
//        int section;
//        try {
//            section = Integer.parseInt(sectionStr);
//        } catch (NumberFormatException e) {
//            return null;
//        }
//
//        int eventId;
//        try {
//            eventId = Integer.parseInt(eventIdStr);
//        } catch (NumberFormatException e) {
//            return null;
//        }
//
//        List<Integer> seats;
//        try {
//            seats = Arrays.stream(seatsStr.split(","))
//                    .map(String::trim)
//                    .filter(s -> !s.isEmpty())
//                    .map(Integer::parseInt)
//                    .toList();
//        } catch (NumberFormatException e) {
//            return null;
//        }
//
//        if (seats.isEmpty()) return null;
//
//        return new HoldRecord(holdId, storedUserId, eventId, section, row, seats);
//    }

    public record InventoryDeductionEvent(
            String orderId,
            Integer eventId,
            Integer section,
            String row,
            List<Integer> seats
    ) {
    }

    @RabbitListener(queues = "q.inventory.inventory_deduction")
    @Transactional
    public void deductInventory(InventoryDeductionEvent event) {
        String orderId = event.orderId();
        int section = event.section();
        String row = event.row();

        try {
            List<Seat> seats = seatRepository.findSeatsForUpdate(event.eventId(), section, row, event.seats());

            if (seats.size() != event.seats().size()) {
                log.error("Seat count mismatch during deduction. expected={}, found={}, orderId={}, section={}, row={}",
                        event.seats().size(), seats.size(), orderId, section, row);
                outboxMessageRepository.save(outboxEntry(
                        RabbitConfig.INVENTORY_EXCHANGE,
                        RabbitConfig.INVENTORY_DEDUCTION_FAILED_KEY,
                        Map.of("orderId", orderId)));
                return;
            }

            boolean alreadyProcessed = seats.stream().allMatch(s ->
                    s.getStatus() == SeatStatus.SOLD && orderId.equals(s.getOrderId())
            );

            if (alreadyProcessed) return;

            boolean allAvailable = seats.stream().allMatch(s ->
                    s.getStatus() == SeatStatus.AVAILABLE
            );

            if (!allAvailable) {
                log.error("Seats not available for deduction. orderId={}, section={}, row={}", orderId, section, row);
                outboxMessageRepository.save(outboxEntry(
                        RabbitConfig.INVENTORY_EXCHANGE,
                        RabbitConfig.INVENTORY_DEDUCTION_FAILED_KEY,
                        Map.of("orderId", orderId)));
                return;
            }

            seats.forEach(s -> {
                s.setOrderId(orderId);
                s.setStatus(SeatStatus.SOLD);
            });

            seatRepository.saveAll(seats);

            log.info("Inventory deducted. orderId={}, section={}, row={}, seats={}", orderId, section, row, event.seats());
            outboxMessageRepository.save(outboxEntry(
                    RabbitConfig.INVENTORY_EXCHANGE,
                    RabbitConfig.INVENTORY_DEDUCTED_KEY,
                    Map.of("orderId", orderId)));
        } catch (Exception e) {
            log.error("Unexpected error during inventory deduction. orderId={}, section={}, row={}", orderId, section, row, e);
            throw e;
        }
    }


    private OutboxMessage outboxEntry(String exchange, String routingKey, Map<String, Object> payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setExchange(exchange);
        msg.setRoutingKey(routingKey);
        msg.setPayload(payload);
        msg.setStatus(OutboxStatus.PENDING);
        return msg;
    }

    public void loadInventory(int eventId) {
        List<Seat> seats = seatRepository.findSeatsByEventId(eventId);

        Map<String, List<Seat>> seatsByRow = seats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getSection() + ":" + s.getRow()
                ));

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

            var keySer = redisTemplate.getStringSerializer();

            for (var entry : seatsByRow.entrySet()) {
                String sectionRow = entry.getKey();
                List<Seat> rowSeats = entry.getValue();

                String redisKey = "seats:" + eventId + ":" + sectionRow;
                byte[] rawKey = keySer.serialize(redisKey);

                int maxSeatNumber = rowSeats.stream()
                        .mapToInt(Seat::getNumber)
                        .max()
                        .orElse(0);

                if (maxSeatNumber == 0) continue;

                connection.setBit(rawKey, maxSeatNumber - 1L, false);

                String capKey = redisKey + ":cap";
                byte[] rawCapKey = keySer.serialize(capKey);
                connection.set(rawCapKey, keySer.serialize(String.valueOf(maxSeatNumber)));

                String pricesKey = "prices:" + eventId + ":" + sectionRow;
                byte[] rawPricesKey = keySer.serialize(pricesKey);

                for (Seat seat : rowSeats) {
                    if (seat.getStatus() != SeatStatus.AVAILABLE) {
                        connection.setBit(rawKey, seat.getNumber() - 1L, true);
                    }
                    if (seat.getPrice() != null) {
                        connection.hSet(rawPricesKey,
                                keySer.serialize(String.valueOf(seat.getNumber())),
                                keySer.serialize(seat.getPrice().toPlainString()));
                    }
                }
            }
            return null;
        });
    }

    public boolean isHoldActive(String holdId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("hold:" + holdId));
    }

    public record HoldReleaseEvent(
            Integer eventId,
            Integer section,
            String row,
            List<Integer> seats
    ) {
    }

    @RabbitListener(queues = "q.inventory.hold_release")
    public void releaseHold(HoldReleaseEvent event) {
        String rowKey = "seats:" + event.eventId() + ":" + event.section() + ":" + event.row();
        List<String> offsets = event.seats().stream()
                .map(n -> String.valueOf(n - 1))
                .toList();
        redisTemplate.execute(
                releaseSeatsScript,
                Collections.singletonList(rowKey),
                offsets.toArray(new String[0])
        );
        log.debug("Released hold. eventId={}, section={}, row={}, seats={}",
                event.eventId(), event.section(), event.row(), event.seats());
    }

    public record WarmCacheEvent(Integer eventId) {
    }

    @RabbitListener(queues = "q.inventory.warm_cache")
    public void onWarmCache(WarmCacheEvent event) {
        log.debug("Cache warm triggered. eventId={}", event.eventId());
        try {
            loadInventory(event.eventId());
        } catch (Exception e) {
            log.error("Cache warm failed. eventId={}", event.eventId(), e);
            outboxMessageRepository.save(outboxEntry(
                    RabbitConfig.INVENTORY_EXCHANGE,
                    RabbitConfig.CACHE_WARM_FAILED_KEY,
                    Map.of("eventId", event.eventId(), "error", e.getMessage())));
        }
    }

}
