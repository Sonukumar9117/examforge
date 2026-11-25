package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything the exam hall needs. Contains NO correct answers. Clients must compute remaining time
 * from {@code serverNow} and {@code deadlineAt}, not from the device clock.
 */
public record AttemptResponse(
        Long attemptId,
        int attemptNo,
        String status,
        ExamInfo exam,
        Instant startedAt,
        Instant deadlineAt,
        Instant serverNow,
        Instant submittedAt,
        List<QuestionItem> questions,
        List<AnswerState> answers) {

    public record ExamInfo(Long id, String title, String slug, String categoryName, int durationMinutes,
                           boolean allowNavigation, BigDecimal totalMarks) {}

    public record QuestionItem(Long questionId, int number, String type, String text, String imageUrl,
                               String subject, BigDecimal marks, BigDecimal negativeMarks, List<OptionItem> options) {}

    public record OptionItem(Long id, String label, String text) {}

    public record AnswerState(Long questionId, List<Long> selectedOptionIds, boolean markedForReview, boolean visited,
                              long seq) {}
}
