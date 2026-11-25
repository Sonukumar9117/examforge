package com.examforge.dto.request;

import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamType;
import com.examforge.entity.enums.ResultRelease;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record ExamRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 10_000) String instructions,
        @NotNull ExamType type,
        @NotNull Difficulty difficulty,
        @Min(1) @Max(600) int durationMinutes,
        @DecimalMin("0.0") @DecimalMax("10000.0") BigDecimal passMarks,
        Instant startsAt,
        Instant endsAt,
        @Min(1) @Max(100) Integer maxAttempts,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        boolean allowNavigation,
        @NotNull ResultRelease resultRelease) {}
