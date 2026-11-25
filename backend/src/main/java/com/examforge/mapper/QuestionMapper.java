package com.examforge.mapper;

import com.examforge.dto.response.QuestionResponse;
import com.examforge.entity.Question;

import java.util.TreeSet;

public final class QuestionMapper {
    private QuestionMapper() {}

    public static QuestionResponse toResponse(Question q) {
        return new QuestionResponse(q.getId(), CatalogMapper.toResponse(q.getSubject()), q.getTopic(), q.getType().name(),
                q.getQuestionText(), q.getImageUrl(), q.getExplanation(), q.getDifficulty().name(), q.getLanguage(),
                q.getMarks(), q.getNegativeMarks(), q.getSource(), q.getStatus().name(), new TreeSet<>(q.getTags()),
                q.getOptions().stream()
                        .map(o -> new QuestionResponse.OptionResponse(o.getId(), o.getLabel(), o.getOptionText(), o.isCorrect()))
                        .toList(),
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
