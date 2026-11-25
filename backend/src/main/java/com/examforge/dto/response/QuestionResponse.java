package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Full question including correct answers — admin endpoints only. */
public record QuestionResponse(
        Long id,
        SubjectResponse subject,
        String topic,
        String type,
        String questionText,
        String imageUrl,
        String explanation,
        String difficulty,
        String language,
        BigDecimal marks,
        BigDecimal negativeMarks,
        String source,
        String status,
        Set<String> tags,
        List<OptionResponse> options,
        Instant createdAt,
        Instant updatedAt) {

    public record OptionResponse(Long id, String label, String text, boolean correct) {}
}
