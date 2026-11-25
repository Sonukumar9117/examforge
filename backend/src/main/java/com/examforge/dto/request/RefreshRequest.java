package com.examforge.dto.request;

import jakarta.validation.constraints.Size;

/** Mobile clients send the refresh token in the body; web clients rely on the HttpOnly cookie. */
public record RefreshRequest(@Size(max = 200) String refreshToken) {}
