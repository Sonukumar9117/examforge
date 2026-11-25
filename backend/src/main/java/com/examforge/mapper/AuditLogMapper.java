package com.examforge.mapper;

import com.examforge.dto.response.AuditLogResponse;
import com.examforge.entity.AuditLog;

public final class AuditLogMapper {
    private AuditLogMapper() {}

    public static AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorUsername(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
    }
}
