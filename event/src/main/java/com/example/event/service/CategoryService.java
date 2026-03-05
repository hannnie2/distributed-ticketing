package com.example.event.service;

import com.example.event.dto.request.CategoryInDto;
import com.example.event.dto.response.CategoryOutDto;
import com.example.event.entity.Category;
import com.example.event.exception.ResourceNotFoundException;
import com.example.event.mapper.CategoryMapper;
import com.example.event.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryOutDto create(CategoryInDto request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Category name already exists");
        }
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public Page<CategoryOutDto> findAll(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(categoryMapper::toResponse);
    }

    public CategoryOutDto findById(Long id) {
        return categoryMapper.toResponse(getOrThrow(id));
    }

    public CategoryOutDto update(Long id, CategoryInDto request) {
        Category category = getOrThrow(id);
        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Category name already exists");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        getOrThrow(id);
        categoryRepository.deleteById(id);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
