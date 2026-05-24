package com.example.inventory.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitConfig {
    public static final String INVENTORY_EXCHANGE = "x.inventory";
    public static final String INVENTORY_DEDUCTED_KEY = "inventory_deducted";
    public static final String INVENTORY_DEDUCTION_FAILED_KEY = "inventory_deduction_failed";
    public static final String CACHE_WARM_FAILED_KEY = "cache_warm_failed";
    private static final String PAYMENT_EXCHANGE = "x.payment";
    private static final String ORDER_PAID_KEY = "order_paid";
    public static final String ORDER_EXCHANGE = "x.order";
    private static final String ORDER_CANCELLED_KEY = "order_cancelled";
    private static final String ORDER_CONFIRMED_KEY = "order_confirmed";
    private static final String HOLD_RELEASE_QUEUE = "q.inventory.hold_release";
    public static final String SHIP_ORDER_QUEUE = "q.inventory.ship_order";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public DirectExchange inventoryExchange() {
        return new DirectExchange(INVENTORY_EXCHANGE);
    }

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue holdReleaseQueue() {
        return QueueBuilder.durable(HOLD_RELEASE_QUEUE).build();
    }

    @Bean
    public Binding holdReleaseQueueBinding(Queue holdReleaseQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(holdReleaseQueue).to(orderExchange)
                .with(ORDER_CANCELLED_KEY);
    }

    @Bean
    public Queue shipOrderQueue() {
        return QueueBuilder.durable(SHIP_ORDER_QUEUE).build();
    }

    @Bean
    public Binding shipOrderQueueBinding(Queue shipOrderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(shipOrderQueue).to(orderExchange)
                .with(ORDER_CONFIRMED_KEY);
    }

    @Bean
    public Queue inventoryDeductionQueue() {
        return QueueBuilder.durable("q.inventory.inventory_deduction").build();
    }

    @Bean
    public Binding inventoryDeductionQueueBinding(Queue inventoryDeductionQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(inventoryDeductionQueue).to(paymentExchange)
                .with(ORDER_PAID_KEY);
    }

    // After inventory.deducted is published (by deductInventory), this queue triggers
    // convertSold against Redis to remove the hold hash + userhold key. Bits stay set.
    @Bean
    public Queue convertSoldQueue() {
        return QueueBuilder.durable("q.inventory.convert_sold").build();
    }

    @Bean
    public Binding convertSoldQueueBinding(Queue convertSoldQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(convertSoldQueue).to(inventoryExchange)
                .with(INVENTORY_DEDUCTED_KEY);
    }

    @Bean
    public Queue cacheWarmFailedQueue() {
        return QueueBuilder.durable("q.admin.cache_warm_failed").build();
    }

    @Bean
    public Binding cacheWarmFailedQueueBinding(Queue cacheWarmFailedQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(cacheWarmFailedQueue).to(inventoryExchange)
                .with(CACHE_WARM_FAILED_KEY);
    }

    // Processing queue — receives messages dead-lettered from the delay queue
    @Bean
    public Queue warmCacheQueue() {
        return new Queue("q.inventory.warm_cache", true);
    }

    // Delay queue — messages sit here until their per-message TTL expires,
    // then get dead-lettered to the default exchange → q.inventory.warm_cache
    @Bean
    public Queue warmCacheDelayQueue() {
        return QueueBuilder.durable("q.inventory.warm_cache.delay")
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", "q.inventory.warm_cache")
                .build();
    }
}
