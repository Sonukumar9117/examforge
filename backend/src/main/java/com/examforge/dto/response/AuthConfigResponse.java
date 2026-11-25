package com.examforge.dto.response;

public record AuthConfigResponse(boolean googleEnabled, String googleClientId, boolean emailVerificationRequired) {}
