package com.examforge.controller;

import com.examforge.dto.request.UpdateRolesRequest;
import com.examforge.dto.request.UpdateStatusRequest;
import com.examforge.dto.response.AdminStatsResponse;
import com.examforge.dto.response.AdminUserResponse;
import com.examforge.dto.response.AuditLogResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.AdminStatsService;
import com.examforge.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Admin-only endpoints. Protected twice: URL rule in SecurityConfig and @PreAuthorize here. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {
    private final AdminUserService adminUsers;
    private final AdminStatsService adminStats;

    public AdminController(AdminUserService adminUsers, AdminStatsService adminStats) {
        this.adminUsers = adminUsers;
        this.adminStats = adminStats;
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminStats.stats();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminUsers.list(q, role, page, size);
    }

    @PutMapping("/users/{id}/roles")
    public AdminUserResponse updateRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody UpdateRolesRequest req, HttpServletRequest http) {
        return adminUsers.updateRoles(CurrentUser.id(jwt), id, req, http.getRemoteAddr());
    }

    @PutMapping("/users/{id}/status")
    public AdminUserResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest req, HttpServletRequest http) {
        return adminUsers.updateStatus(CurrentUser.id(jwt), id, req.enabled(), http.getRemoteAddr());
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) String action,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return adminUsers.auditLogs(action, page, size);
    }
}
