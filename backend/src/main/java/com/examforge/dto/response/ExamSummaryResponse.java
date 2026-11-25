package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Exam card/list data. Never contains questions or answers. */
public record ExamSummaryResponse(
        Long id,
        String title,
        String slug,
        CategoryRef category,
        String type,
        String difficulty,
        String status,
        int durationMinutes,
        int questionCount,
        BigDecimal totalMarks,
        BigDecimal passMarks,
        Instant startsAt,
        Instant endsAt,
        Integer maxAttempts,
        Instant publishedAt,
        Instant updatedAt) {

    public record CategoryRef(Long id, String name, String slug, String icon) {}
}
