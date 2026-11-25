package com.examforge.dto.request;

import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record QuestionRequest(
        @NotNull Long subjectId,
        @Size(max = 100) String topic,
        @NotNull QuestionType type,
        @NotBlank @Size(max = 10_000) String questionText,
        @Size(max = 500) @Pattern(regexp = "^$|^https://.+", message = "must be an https URL") String imageUrl,
        @Size(max = 10_000) String explanation,
        @NotNull Difficulty difficulty,
        @Size(max = 10) String language,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal marks,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal negativeMarks,
        @Size(max = 255) String source,
        QuestionStatus status,
        @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
        @NotNull @Size(min = 2, max = 8) List<@Valid OptionRequest> options) {

    public record OptionRequest(@NotBlank @Size(max = 2000) String text, boolean correct) {}
}
