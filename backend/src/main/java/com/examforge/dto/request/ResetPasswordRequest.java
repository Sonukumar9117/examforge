package com.examforge.dto.request;

import com.examforge.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD_REGEX, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword) {}
