package com.examforge.dto.response;

import java.time.Instant;

public record SaveAnswersResponse(Instant savedAt, Instant serverNow, Instant deadlineAt, String status) {}
