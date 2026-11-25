package com.examforge.dto.response;

import java.time.Instant;

public record AuditLogResponse(Long id, Long actorId, String actorUsername, String action, String entityType,
                               String entityId, String details, String ipAddress, Instant createdAt) {}
