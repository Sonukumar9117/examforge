package com.examforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code identifier} is either the email address or the username. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(max = 128) String password,
        boolean rememberMe) {}
