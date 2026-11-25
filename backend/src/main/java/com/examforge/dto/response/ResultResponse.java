package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Result of a finished attempt. {@code review} (answers + explanations) is null until the exam's
 * result-release rule allows it; {@code reviewMessage} explains when it will be available.
 * Rank and percentile are only filled once enough students have taken the exam.
 */
public record ResultResponse(
        Long attemptId,
        int attemptNo,
        String status,
        AttemptSummaryResponse.ExamRef exam,
        BigDecimal score,
        BigDecimal maxScore,
        BigDecimal percentage,
        BigDecimal accuracy,
        BigDecimal passMarks,
        Boolean passed,
        int totalQuestions,
        int correct,
        int incorrect,
        int unanswered,
        int timeTakenSeconds,
        Instant submittedAt,
        Long rank,
        BigDecimal percentile,
        long candidates,
        /* why rank is not shown yet, if it is not */
        String rankNote,
        boolean leaderboardAvailable,
        List<SubjectScore> subjects,
        boolean reviewAvailable,
        String reviewMessage,
        List<ReviewItem> review,
        List<ExamSummaryResponse> recommendations) {

    public record SubjectScore(Long subjectId, String subject, BigDecimal score, BigDecimal maxScore, int correct,
                               int incorrect, int unanswered, BigDecimal accuracy) {}

    public record ReviewItem(int number, Long questionId, String type, String text, String imageUrl, String subject,
                             String topic, BigDecimal marks, BigDecimal negativeMarks, BigDecimal marksAwarded,
                             String outcome, String explanation, boolean saved, List<ReviewOption> options) {}

    public record ReviewOption(Long id, String label, String text, boolean correct, boolean selected) {}
}
