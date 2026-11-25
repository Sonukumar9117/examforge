package com.examforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** One attempt in an admin report / CSV export. Score fields are null while in progress. */
public record AttemptReportRow(Long attemptId, Long userId, String username, String fullName, String email, int attemptNo,
                               String status, Instant startedAt, Instant submittedAt, BigDecimal score,
                               BigDecimal maxScore, BigDecimal percentage, Integer correct, Integer incorrect,
                               Integer unanswered, Integer timeTakenSeconds, Boolean passed) {}
