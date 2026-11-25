package com.examforge.dto.response;

import java.time.Instant;
import java.util.List;

public record SavedQuestionResponse(Long id, String note, Instant createdAt, Long questionId, String type, String text,
                                    String subject, String topic, String explanation,
                                    List<ResultResponse.ReviewOption> options) {}
