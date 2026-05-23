package com.example.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String PAYMENT_EXCHANGE = "x.payment";
    public static final String PAYMENT_SUCCEEDED_KEY = "payment_succeeded";
    public static final String REFUND_REQUIRED_KEY = "refund_required";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE);
    }

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
    public Queue refundRequiredQueue() {
        return QueueBuilder.durable("q.payment.refund_required").build();
    }

    @Bean
    public Binding refundRequiredQueueBinding(Queue refundRequiredQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(refundRequiredQueue).to(paymentExchange).with(REFUND_REQUIRED_KEY);
    }
}
