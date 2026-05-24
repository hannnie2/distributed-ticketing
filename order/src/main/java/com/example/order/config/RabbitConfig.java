package com.example.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(RabbitQueue.ORDER_EXCHANGE);
    }

    @Bean
    public DirectExchange inventoryExchange() {
        return new DirectExchange(RabbitQueue.INVENTORY_EXCHANGE);
    }

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(RabbitQueue.PAYMENT_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitQueue.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue orderConfirmationQueue() {
        return QueueBuilder.durable(RabbitQueue.ORDER_CONFIRMATION_QUEUE).build();
    }

    @Bean
    public Binding orderConfirmationQueueBinding(Queue orderConfirmationQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(orderConfirmationQueue).to(inventoryExchange)
                .with(RabbitQueue.INVENTORY_DEDUCTED_KEY);
    }

    @Bean
    public Queue inventoryDeductionQueue() {
        return QueueBuilder.durable("q.order.inventory_deduction_initiation").build();
    }

    @Bean
    public Binding inventoryDeductionQueueBinding(Queue inventoryDeductionQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(inventoryDeductionQueue).to(paymentExchange)
                .with(RabbitQueue.PAYMENT_SUCCEEDED_KEY);
    }

    @Bean
    public Queue unpaidOrderCheckQueue() {
        return QueueBuilder.durable(RabbitQueue.UNPAID_ORDER_CHECK_QUEUE).ttl(10 * 60 * 1000)
                .withArgument("x-dead-letter-exchange", RabbitQueue.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitQueue.ORDER_EXPIRED_KEY).build();
    }

    @Bean
    public Binding unpaidOrderCheckQueueBinding(Queue unpaidOrderCheckQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(unpaidOrderCheckQueue).to(orderExchange)
                .with(RabbitQueue.ORDER_PENDING_CREATED_KEY);
    }

    @Bean
    public Queue orderExpiredQueue() {
        return QueueBuilder.durable(RabbitQueue.ORDER_EXPIRED_QUEUE).build();
    }

    @Bean
    public Binding orderExpiredQueueBinding(Queue orderExpiredQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderExpiredQueue).to(deadLetterExchange)
                .with(RabbitQueue.ORDER_EXPIRED_KEY);
    }

    @Bean
    public Queue inventoryDeductionFailedQueue() {
        return QueueBuilder.durable(RabbitQueue.INVENTORY_DEDUCTION_FAILED_QUEUE).build();
    }

    @Bean
    public Binding inventoryDeductionFailedQueueBinding(Queue inventoryDeductionFailedQueue, DirectExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryDeductionFailedQueue).to(inventoryExchange)
                .with(RabbitQueue.INVENTORY_DEDUCTION_FAILED_KEY);
    }

    // Delay queue: holds payment-window-check messages for 5 min, then dead-letters
    // to deadLetterExchange with PAYMENT_WINDOW_EXPIRED_KEY.
    @Bean
    public Queue paymentWindowCheckQueue() {
        return QueueBuilder.durable(RabbitQueue.PAYMENT_WINDOW_CHECK_QUEUE).ttl(5 * 60 * 1000)
                .withArgument("x-dead-letter-exchange", RabbitQueue.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitQueue.PAYMENT_WINDOW_EXPIRED_KEY).build();
    }

    @Bean
    public Binding paymentWindowCheckQueueBinding(Queue paymentWindowCheckQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentWindowCheckQueue).to(orderExchange)
                .with(RabbitQueue.PAYMENT_WINDOW_INITIATED_KEY);
    }

    @Bean
    public Queue paymentWindowExpiredQueue() {
        return QueueBuilder.durable(RabbitQueue.PAYMENT_WINDOW_EXPIRED_QUEUE).build();
    }

    @Bean
    public Binding paymentWindowExpiredQueueBinding(Queue paymentWindowExpiredQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentWindowExpiredQueue).to(deadLetterExchange)
                .with(RabbitQueue.PAYMENT_WINDOW_EXPIRED_KEY);
    }
}
