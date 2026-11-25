package com.examforge.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** What a student sees before starting: rules and overview, never questions or answers. */
public record ExamPublicResponse(
        ExamSummaryResponse summary,
        String description,
        String instructions,
        boolean allowNavigation,
        boolean negativeMarking,
        BigDecimal maxNegativeMarks,
        List<SubjectResponse> subjects) {}
