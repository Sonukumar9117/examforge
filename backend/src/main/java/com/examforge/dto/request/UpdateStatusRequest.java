package com.examforge.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull Boolean enabled) {}
