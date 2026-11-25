package com.examforge.mapper;

import com.examforge.dto.response.ExamDetailResponse;
import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.entity.Exam;
import com.examforge.entity.ExamCategory;

public final class ExamMapper {
    private ExamMapper() {}

    public static ExamSummaryResponse toSummary(Exam e) {
        ExamCategory c = e.getCategory();
        return new ExamSummaryResponse(e.getId(), e.getTitle(), e.getSlug(),
                new ExamSummaryResponse.CategoryRef(c.getId(), c.getName(), c.getSlug(), c.getIcon()),
                e.getType().name(), e.getDifficulty().name(), e.getStatus().name(), e.getDurationMinutes(),
                e.getQuestions().size(), e.totalMarks(), e.getPassMarks(), e.getStartsAt(), e.getEndsAt(),
                e.getMaxAttempts(), e.getPublishedAt(), e.getUpdatedAt());
    }

    public static ExamDetailResponse toDetail(Exam e) {
        return new ExamDetailResponse(toSummary(e), e.getDescription(), e.getInstructions(), e.isShuffleQuestions(),
                e.isShuffleOptions(), e.isAllowNavigation(), e.getResultRelease().name(), e.getResultsReleasedAt(),
                e.getQuestions().stream()
                        .map(eq -> new ExamDetailResponse.Item(eq.getDisplayOrder(), eq.getMarks(), eq.getNegativeMarks(),
                                QuestionMapper.toResponse(eq.getQuestion())))
                        .toList());
    }
}
