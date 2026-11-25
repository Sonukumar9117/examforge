package com.examforge.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Autosave / final save of answer states. {@code seq} is a number the client increases with every change;
 * the server ignores an item whose seq is not newer than what it already has (late or repeated requests).
 */
public record SaveAnswersRequest(@NotNull @Size(max = 500) List<@Valid Item> answers) {

    public record Item(
            @NotNull Long questionId,
            @Size(max = 8) List<Long> selectedOptionIds,
            boolean markedForReview,
            boolean visited,
            @Min(0) long seq) {}
}
