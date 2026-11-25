package com.examforge.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** The full ordered question list of an exam (replaces the previous list). Marks default to the question's own. */
public record ExamQuestionsRequest(@NotNull @Size(max = 500) List<@Valid Item> questions) {

    public record Item(
            @NotNull Long questionId,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal marks,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal negativeMarks) {}
}
