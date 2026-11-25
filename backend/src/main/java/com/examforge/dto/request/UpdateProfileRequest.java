package com.examforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @Size(max = 500) String bio,
        @Pattern(regexp = "^$|^[+0-9 ()-]{6,20}$", message = "is not a valid phone number") String phone,
        @Size(max = 500) @Pattern(regexp = "^$|^https://.+", message = "must be an https URL") String avatarUrl,
        Long targetCategoryId) {}
