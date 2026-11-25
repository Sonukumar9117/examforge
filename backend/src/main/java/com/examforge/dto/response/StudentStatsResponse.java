package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Numbers for the student dashboard. Averages are null until the first test is finished. */
public record StudentStatsResponse(
        long testsTaken,
        BigDecimal averagePercentage,
        BigDecimal bestPercentage,
        BigDecimal accuracy,
        long totalTimeSeconds,
        int currentStreak,
        int longestStreak,
        List<AttemptSummaryResponse> inProgress,
        List<AttemptSummaryResponse> recent,
        List<TrendPoint> trend,
        List<SubjectAccuracy> subjects) {

    public record TrendPoint(Instant date, String examTitle, BigDecimal percentage) {}

    public record SubjectAccuracy(Long subjectId, String subject, long correct, long incorrect, long unanswered,
                                  BigDecimal accuracy) {}
}
