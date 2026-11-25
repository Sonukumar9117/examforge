package com.examforge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @Size(max = 50) @Pattern(regexp = "^$|^[A-Za-z0-9]+$", message = "must be an icon name like Landmark") String icon,
        @Min(0) @Max(10_000) int displayOrder,
        boolean active,
        Set<Long> subjectIds) {}
