package com.example.event.mapper;

import com.example.event.dto.response.CategoryOutDto;
import com.example.event.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryOutDto toResponse(Category category) {
        if (category == null) return null;
        return CategoryOutDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
