package com.examforge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** The single error shape returned by every endpoint. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String code, String message, Map<String, String> fieldErrors,
                       Instant timestamp, String path) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(status, code, message, null, Instant.now(), path);
    }
}
