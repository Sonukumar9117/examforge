package com.examforge.dto.request;

import com.examforge.entity.enums.QuestionStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record BulkQuestionStatusRequest(@NotEmpty @Size(max = 500) Set<Long> ids, @NotNull QuestionStatus status) {}
