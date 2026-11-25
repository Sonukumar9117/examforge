package com.examforge.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavedQuestionRequest(@NotNull Long questionId, @Size(max = 500) String note) {}
