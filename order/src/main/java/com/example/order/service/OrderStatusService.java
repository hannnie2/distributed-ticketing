package com.example.order.service;

import com.example.order.constants.OrderStatus;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderStatusService {

    private static final long SSE_TIMEOUT_MS = 60_000;
    private static final String CHANNEL_PREFIX = "order-status:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final OrderRepository orderRepository;

    private final Map<Long, EmitterEntry> emitters = new ConcurrentHashMap<>();

    public OrderStatus getOrderStatus(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId))
                .getStatus();
    }

    public SseEmitter subscribe(long orderId) {
        // Only CONFIRMED/CANCELLED are terminal. AWAITING_PAYMENT is in-flight —
        // the subscribe must wait for reconcile/janitor to drive it to a terminal state.
        OrderStatus currentStatus = getOrderStatus(orderId);
        if (currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.CANCELLED) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(currentStatus.name()));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + orderId);

        MessageListener listener = (message, pattern) -> {
            String status = new String(message.getBody());
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(status));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };

        EmitterEntry entry = new EmitterEntry(emitter, listener, topic);
        emitters.put(orderId, entry);
        listenerContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> {
            emitters.remove(orderId);
            listenerContainer.removeMessageListener(listener, topic);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            stringRedisTemplate.convertAndSend(
                    CHANNEL_PREFIX + event.orderId(),
                    event.status().name());
        } catch (Exception e) {
            log.error("Failed to publish order status change to Redis. orderId={}, status={}",
                    event.orderId(), event.status(), e);
        }
    }

    private record EmitterEntry(SseEmitter emitter, MessageListener listener, ChannelTopic topic) {
    }
}
