package com.examforge.dto.response;

import java.util.List;

public record ImportReportResponse(int totalRows, int validRows, int imported, boolean dryRun, List<RowError> errors) {

    /** {@code row} is 1-based: the CSV line number (header = line 1) or the JSON array position. */
    public record RowError(int row, String message) {}
}
