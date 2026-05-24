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
import com.example.inventory.util.RedisKeys;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final RedisTemplate<String, String> redisTemplate;
    private final SeatRepository seatRepository;
    private final RedisScript<List<Object>> holdSeatsScript;
    private final RedisScript<Long> releaseSeatsScript;
    private final RedisScript<Long> convertSoldScript;

    private final OutboxMessageRepository outboxMessageRepository;

    // Idem keys outlive a single hold's lifecycle but should not accumulate forever.
    // 24h is far longer than any realistic Spring Retry window and trivially cheap.
    private static final long IDEM_TTL_SECONDS = 24 * 60 * 60;

    public HoldSeatsOutDto holdSeats(String userId, HoldSeatsInDto holdSeatsInDto) {
        int eventId = holdSeatsInDto.eventId();
        String idempotencyKey = holdSeatsInDto.idempotencyKey();
        List<SeatDto> seats = holdSeatsInDto.seats();
        SeatDto first = seats.get(0);
        int section = first.section();
        String row = first.row();

        log.debug("Holding seats. eventId={}, section={}, row={}, idem={}",
                eventId, section, row, idempotencyKey);

        List<String> offsets = seats.stream()
                .map(s -> String.valueOf(s.number() - 1))
                .distinct()
                .toList();

        UUID holdId = UUID.randomUUID();
        String bitsKey = RedisKeys.bits(eventId, section, row);
        String capKey = RedisKeys.cap(eventId, section, row);
        String idemKey = RedisKeys.idem(eventId, section, row, idempotencyKey);
        String holdKey = RedisKeys.hold(eventId, section, row, holdId.toString());

        List<String> argv = new ArrayList<>();
        argv.add(holdId.toString());
        argv.add(String.valueOf(IDEM_TTL_SECONDS));
        argv.addAll(offsets);

        List<Object> result = redisTemplate.execute(
                holdSeatsScript,
                List.of(bitsKey, capKey, idemKey, holdKey),
                (Object[]) argv.toArray(new String[0])
        );

        if (result.isEmpty()) {
            throw new IllegalStateException("Redis hold script returned no result");
        }

        long status = (Long) result.get(0);

        if (status == 0) {
            log.debug("Seat hold failed. eventId={}, section={}, row={}, seats={}, reason={}",
                    eventId, section, row, offsets, result.size() > 1 ? result.get(1) : "unknown");
            throw new SeatUnavailableException("Seats unavailable");
        }

        if (status == 2) {
            holdId = UUID.fromString((String) result.get(1));
            log.debug("Existing hold returned on idempotent retry. holdId={}, eventId={}", holdId, eventId);
        }

        // price lookup — if this fails, client retries and the script is idempotent
        List<String> seatNumbers = seats.stream()
                .map(s -> String.valueOf(s.number()))
                .toList();

        String pricesKey = RedisKeys.prices(eventId, section, row);
        List<Object> priceValues = redisTemplate.opsForHash().multiGet(pricesKey, new ArrayList<>(seatNumbers));

        List<SeatPriceDto> seatPrices = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            SeatDto seat = seats.get(i);
            String priceStr = (String) priceValues.get(i);
            BigDecimal price = priceStr != null ? new BigDecimal(priceStr) : null;
            seatPrices.add(new SeatPriceDto(section, row, seat.number(), price));
        }

        return new HoldSeatsOutDto(holdId, seatPrices);
    }

    public record InventoryDeductionEvent(
            String orderId,
            String holdId,
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
        int eventId = event.eventId();
        int section = event.section();
        String row = event.row();

        try {
            List<Seat> seats = seatRepository.findSeatsForUpdate(eventId, section, row, event.seats());

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

            Map<String, Object> deductedPayload = new HashMap<>();
            deductedPayload.put("orderId", orderId);
            deductedPayload.put("holdId", event.holdId());
            deductedPayload.put("eventId", eventId);
            deductedPayload.put("section", section);
            deductedPayload.put("row", row);
            outboxMessageRepository.save(outboxEntry(
                    RabbitConfig.INVENTORY_EXCHANGE,
                    RabbitConfig.INVENTORY_DEDUCTED_KEY,
                    deductedPayload));

            log.info("Inventory deducted. orderId={}, section={}, row={}, seats={}", orderId, section, row, event.seats());
        } catch (Exception e) {
            log.error("Unexpected error during inventory deduction. orderId={}, section={}, row={}", orderId, section, row, e);
            throw e;
        }
    }

    // After DB deduction commits, run convertSold against the Redis hold so the hash
    // is cleaned up. Bits stay set (sold).
    @RabbitListener(queues = "q.inventory.convert_sold")
    public void onInventoryDeducted(InventoryDeductedEvent event) {
        String holdKey = RedisKeys.hold(event.eventId(), event.section(), event.row(), event.holdId());
        try {
            redisTemplate.execute(convertSoldScript, List.of(holdKey));
            log.debug("Converted hold to sold. orderId={}, holdId={}", event.orderId(), event.holdId());
        } catch (Exception e) {
            // Surface to the consumer so it can nack and retry. Bits are already SOLD in DB,
            // so the hold hash leaking briefly only confuses the orphan reconciler.
            log.error("convertSold failed. orderId={}, holdId={}", event.orderId(), event.holdId(), e);
            throw e;
        }
    }

    public record InventoryDeductedEvent(
            String orderId,
            String holdId,
            Integer eventId,
            Integer section,
            String row
    ) {
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

        // Group by (section, row) so each Redis row gets one bitmap + cap + prices entry.
        Map<SectionRow, List<Seat>> seatsByRow = seats.stream()
                .collect(Collectors.groupingBy(s -> new SectionRow(s.getSection(), s.getRow())));

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var keySer = redisTemplate.getStringSerializer();

            for (var entry : seatsByRow.entrySet()) {
                int section = entry.getKey().section();
                String row = entry.getKey().row();
                List<Seat> rowSeats = entry.getValue();

                byte[] rawBitsKey = keySer.serialize(RedisKeys.bits(eventId, section, row));
                byte[] rawCapKey = keySer.serialize(RedisKeys.cap(eventId, section, row));
                byte[] rawPricesKey = keySer.serialize(RedisKeys.prices(eventId, section, row));

                int maxSeatNumber = rowSeats.stream().mapToInt(Seat::getNumber).max().orElse(0);
                if (maxSeatNumber == 0) continue;

                connection.setBit(rawBitsKey, maxSeatNumber - 1L, false);
                connection.set(rawCapKey, keySer.serialize(String.valueOf(maxSeatNumber)));

                for (Seat seat : rowSeats) {
                    if (seat.getStatus() != SeatStatus.AVAILABLE) {
                        connection.setBit(rawBitsKey, seat.getNumber() - 1L, true);
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

    private record SectionRow(int section, String row) {}

    public record HoldReleaseEvent(
            Integer eventId,
            Integer section,
            String row,
            List<Integer> seats,
            String holdId
    ) {
    }

    @RabbitListener(queues = "q.inventory.hold_release")
    public void releaseHold(HoldReleaseEvent event) {
        releaseHoldDirect(event.eventId(), event.section(), event.row(),
                event.holdId(), event.seats());
    }

    // Synchronous release used by both the queue listener and the HTTP releaseHold endpoint
    // that order service calls for compensating releases in createOrder's catch-block.
    public void releaseHoldDirect(int eventId, int section, String row,
                                  String holdId, List<Integer> seats) {
        String bitsKey = RedisKeys.bits(eventId, section, row);
        String holdKey = RedisKeys.hold(eventId, section, row, holdId);

        String[] offsets = seats.stream()
                .map(n -> String.valueOf(n - 1))
                .toArray(String[]::new);

        try {
            redisTemplate.execute(
                    releaseSeatsScript,
                    List.of(bitsKey, holdKey),
                    (Object[]) offsets
            );
            log.debug("Released hold. eventId={}, section={}, row={}, holdId={}, seats={}",
                    eventId, section, row, holdId, seats);
        } catch (Exception e) {
            // Propagate so the consumer nacks → retry, or the HTTP caller knows
            // compensation failed and can rely on the orphan reconciler.
            log.error("Failed to release hold in Redis. eventId={}, section={}, row={}, holdId={}, seats={}",
                    eventId, section, row, holdId, seats, e);
            throw e;
        }
    }

    public record ShipOrderEvent(Long orderId) {
    }

    @RabbitListener(queues = RabbitConfig.SHIP_ORDER_QUEUE)
    @Transactional
    public void shipOrder(ShipOrderEvent event) {
        log.info("Shipping order. orderId={}", event.orderId());
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
