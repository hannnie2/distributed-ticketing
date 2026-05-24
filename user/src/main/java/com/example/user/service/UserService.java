package com.example.user.service;

import com.example.user.config.RabbitQueue;
import com.example.user.constants.OutboxMessageStatus;
import com.example.user.dto.request.UpdateProfileInDto;
import com.example.user.dto.response.UserOutDto;
import com.example.user.entity.OutboxMessage;
import com.example.user.entity.User;
import com.example.user.exception.UserNotFoundException;
import com.example.user.mapper.UserMapper;
import com.example.user.repository.OutboxMessageRepository;
import com.example.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserOutDto getOrCreate(String cognitoId, String email) {
        return userRepository.findByCognitoId(cognitoId)
                .map(userMapper::toOutDto)
                .orElseGet(() -> create(cognitoId, email));
    }

    @Transactional
    public UserOutDto updateProfile(String cognitoId, UpdateProfileInDto dto) {
        User user = userRepository.findByCognitoId(cognitoId)
                .orElseThrow(() -> new UserNotFoundException(cognitoId));

        user.setName(dto.name());
        userRepository.save(user);

        outboxMessageRepository.save(outboxEntry(
                RabbitQueue.USER_EXCHANGE,
                RabbitQueue.USER_UPDATED_KEY,
                Map.of("cognitoId", user.getCognitoId(), "email", user.getEmail(), "name", user.getName())
        ));

        log.info("User profile updated. cognitoId={}", cognitoId);
        return userMapper.toOutDto(user);
    }

    private UserOutDto create(String cognitoId, String email) {
        User user = new User();
        user.setCognitoId(cognitoId);
        user.setEmail(email);
        userRepository.save(user);

        outboxMessageRepository.save(outboxEntry(
                RabbitQueue.USER_EXCHANGE,
                RabbitQueue.USER_CREATED_KEY,
                Map.of("cognitoId", cognitoId, "email", email)
        ));

        log.info("New user created. cognitoId={}", cognitoId);
        return userMapper.toOutDto(user);
    }

    private OutboxMessage outboxEntry(String exchange, String routingKey, Map<String, Object> payload) {
        OutboxMessage msg = new OutboxMessage();
        msg.setExchange(exchange);
        msg.setRoutingKey(routingKey);
        msg.setPayload(payload);
        msg.setStatus(OutboxMessageStatus.PENDING);
        return msg;
    }
}
