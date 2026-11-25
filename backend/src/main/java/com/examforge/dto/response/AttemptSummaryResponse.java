package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** A row in "My tests". Score fields are null while the attempt is in progress. */
public record AttemptSummaryResponse(
        Long attemptId,
        int attemptNo,
        String status,
        ExamRef exam,
        Instant startedAt,
        Instant deadlineAt,
        Instant submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        BigDecimal percentage,
        Boolean passed) {

    public record ExamRef(Long id, String title, String slug, String categoryName, String categoryIcon) {}
}
