package com.examforge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        /* only returned to mobile clients (X-Client-Type: mobile) */
        String refreshToken,
        UserResponse user,
        boolean emailVerificationRequired) {}
