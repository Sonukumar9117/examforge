package com.examforge.dto.response;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String avatarUrl,
        boolean emailVerified,
        boolean hasPassword,
        boolean googleLinked,
        List<String> roles,
        Instant createdAt,
        ProfileResponse profile) {}
