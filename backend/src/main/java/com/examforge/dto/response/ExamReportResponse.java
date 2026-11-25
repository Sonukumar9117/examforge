package com.examforge.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Admin analytics for one exam, computed from finished attempts only. Averages are null when there are none. */
public record ExamReportResponse(
        ExamSummaryResponse exam,
        long totalAttempts,
        long finishedAttempts,
        long inProgressAttempts,
        long uniqueStudents,
        BigDecimal averageScore,
        BigDecimal averagePercentage,
        BigDecimal highestScore,
        BigDecimal lowestScore,
        BigDecimal passRate,
        Integer averageTimeSeconds,
        List<Bucket> scoreDistribution,
        List<QuestionStat> questions,
        List<SubjectStat> subjects) {

    /** Students whose percentage falls in [from, to). Negative scores count in the first bucket. */
    public record Bucket(int from, int to, long count) {}

    public record QuestionStat(int number, Long questionId, String text, String subject, String difficulty,
                               long correct, long incorrect, long unanswered, BigDecimal accuracy) {}

    public record SubjectStat(String subject, long correct, long incorrect, long unanswered, BigDecimal accuracy) {}
}
