package com.examforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bulk import. {@code content} is the raw file text. With {@code dryRun=true} nothing is saved — the
 * response only reports which rows are valid. Imported questions are always saved as DRAFT for review.
 */
public record QuestionImportRequest(
        @NotNull Format format,
        @NotBlank @Size(max = 2_000_000) String content,
        boolean dryRun) {

    public enum Format { CSV, JSON }
}
