package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Live tests for a student: open right now, coming up, and recently closed (with leaderboards). */
public record LiveTestsResponse(List<Item> open, List<Item> upcoming, List<Item> ended, Instant serverNow) {

    /** {@code myStatus}: null = not taken, IN_PROGRESS, SUBMITTED or AUTO_SUBMITTED. */
    public record Item(ExamSummaryResponse exam, Long myAttemptId, String myStatus, BigDecimal myScore) {}
}
