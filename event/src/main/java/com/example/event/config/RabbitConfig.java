package com.example.event.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SCHEDULED_PUBLISH_QUEUE = "q.event.scheduled_publish";
    public static final String SCHEDULED_PUBLISH_DELAY_QUEUE = "q.event.scheduled_publish.delay";
    public static final String INVENTORY_WARM_CACHE_QUEUE = "q.inventory.warm_cache";
    public static final String INVENTORY_WARM_CACHE_DELAY_QUEUE = "q.inventory.warm_cache.delay";

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

    // Processing queue — receives messages dead-lettered from the delay queue
    @Bean
    public Queue scheduledPublishQueue() {
        return QueueBuilder.durable(SCHEDULED_PUBLISH_QUEUE).build();
    }

    // Delay queue — messages sit here until their per-message TTL expires,
    // then get dead-lettered to the default exchange → q.event.scheduled_publish
    @Bean
    public Queue scheduledPublishDelayQueue() {
        return QueueBuilder.durable(SCHEDULED_PUBLISH_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", SCHEDULED_PUBLISH_QUEUE)
                .build();
    }
}
