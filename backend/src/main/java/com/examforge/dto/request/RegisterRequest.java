package com.examforge.dto.request;

import com.examforge.util.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Pattern(regexp = ValidationPatterns.USERNAME_REGEX, message = ValidationPatterns.USERNAME_MESSAGE)
        String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD_REGEX, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,
        @AssertTrue(message = "you must accept the terms and conditions") boolean acceptTerms) {}
