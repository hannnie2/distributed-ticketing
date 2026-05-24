package com.example.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;


@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(serializer);
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashKeySerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
    }

    @SuppressWarnings("unchecked")
    @Bean
    public RedisScript<List<Object>> holdSeatsScript() {
        return (RedisScript<List<Object>>) (RedisScript<?>) RedisScript.of(new ClassPathResource("scripts/hold_seats.lua"), List.class);
    }

    @Bean
    public RedisScript<Long> releaseSeatsScript() {
        return RedisScript.of(new ClassPathResource("scripts/release_seats.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> convertSoldScript() {
        return RedisScript.of(new ClassPathResource("scripts/convert_sold.lua"), Long.class);
    }

}
