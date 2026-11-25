package com.examforge.security;

import com.examforge.exception.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user id from the access token. Never take a user id from the request body. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Long id(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication is required");
        }
        return Long.valueOf(jwt.getSubject());
    }
}
