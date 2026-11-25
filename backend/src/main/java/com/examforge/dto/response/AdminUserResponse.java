package com.examforge.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(Long id, String username, String email, String fullName, boolean emailVerified,
                                boolean enabled, boolean googleLinked, List<String> roles, Instant createdAt,
                                Instant lastLoginAt) {}
