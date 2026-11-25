package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Admin view of an exam, including its questions with correct answers. */
public record ExamDetailResponse(
        ExamSummaryResponse summary,
        String description,
        String instructions,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        boolean allowNavigation,
        String resultRelease,
        Instant resultsReleasedAt,
        List<Item> questions) {

    public record Item(int order, BigDecimal marks, BigDecimal negativeMarks, QuestionResponse question) {}
}
