package com.example.event.controller;

import com.example.event.dto.request.CategoryInDto;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.CategoryOutDto;
import com.example.event.service.CategoryService;
import com.example.event.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryOutDto>> create(@Valid @RequestBody CategoryInDto request) {
        return Result.success(HttpStatus.CREATED, "Created", categoryService.create(request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CategoryOutDto>>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return Result.success(HttpStatus.OK, "OK", categoryService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryOutDto>> findById(@PathVariable Long id) {
        return Result.success(HttpStatus.OK, "OK", categoryService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryOutDto>> update(@PathVariable Long id,
                                                              @Valid @RequestBody CategoryInDto request) {
        return Result.success(HttpStatus.OK, "OK", categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
