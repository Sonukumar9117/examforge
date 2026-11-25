package com.examforge.service;

import com.examforge.entity.AuditLog;
import com.examforge.entity.User;
import com.examforge.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records important actions (admin changes, role changes, security events). Never log secrets here. */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(User actor, String action, String entityType, Object entityId, String details, String ip) {
        repository.save(new AuditLog(
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(),
                action, entityType, entityId == null ? null : String.valueOf(entityId), details, ip));
    }
}
