package com.examforge.dto.request;

import com.examforge.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} may be empty for Google-only accounts setting their first password. */
public record ChangePasswordRequest(
        @Size(max = 128) String currentPassword,
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD_REGEX, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword) {}
