package com.example.user.mapper;

import com.example.user.dto.response.UserOutDto;
import com.example.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserOutDto toOutDto(User user) {
        return new UserOutDto(
                user.getId(),
                user.getCognitoId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt()
        );
    }
}
