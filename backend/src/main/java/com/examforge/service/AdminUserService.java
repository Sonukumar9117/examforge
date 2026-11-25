package com.examforge.service;

import com.examforge.dto.request.UpdateRolesRequest;
import com.examforge.dto.response.AdminUserResponse;
import com.examforge.dto.response.AuditLogResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.Role;
import com.examforge.entity.User;
import com.examforge.exception.ApiException;
import com.examforge.mapper.AuditLogMapper;
import com.examforge.mapper.UserMapper;
import com.examforge.repository.AuditLogRepository;
import com.examforge.repository.RoleRepository;
import com.examforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(Role.STUDENT, Role.ADMIN);

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String q, String role, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String query = q == null || q.isBlank() ? null : q.trim();
        String roleFilter = role == null || role.isBlank() ? null : role.trim();
        return PageResponse.of(users.search(query, roleFilter, pageable), UserMapper::toAdminResponse);
    }

    @Transactional
    public AdminUserResponse updateRoles(Long actorId, Long userId, UpdateRolesRequest req, String ip) {
        if (!ASSIGNABLE_ROLES.containsAll(req.roles())) {
            throw ApiException.badRequest("INVALID_ROLE", "Allowed roles: " + ASSIGNABLE_ROLES);
        }
        User actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("User"));
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (actor.getId().equals(target.getId()) && !req.roles().contains(Role.ADMIN)) {
            throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "You cannot remove your own admin role");
        }
        Set<String> before = new HashSet<>(target.getRoles().stream().map(Role::getName).toList());
        target.getRoles().clear();
        req.roles().forEach(r -> target.getRoles().add(roles.require(r)));
        // Force a fresh sign-in so new tokens carry the new roles.
        refreshTokens.revokeAll(target.getId());
        audit.record(actor, "USER_ROLES_CHANGED", "User", target.getId(), before + " -> " + req.roles(), ip);
        return UserMapper.toAdminResponse(target);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long actorId, Long userId, boolean enabled, String ip) {
        User actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("User"));
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (actor.getId().equals(target.getId()) && !enabled) {
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "You cannot disable your own account");
        }
        target.setEnabled(enabled);
        if (!enabled) refreshTokens.revokeAll(target.getId());
        audit.record(actor, enabled ? "USER_ENABLED" : "USER_DISABLED", "User", target.getId(), null, ip);
        return UserMapper.toAdminResponse(target);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(String action, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(auditLogs.findByActionContainingIgnoreCase(action == null ? "" : action.trim(), pageable),
                AuditLogMapper::toResponse);
    }
}
