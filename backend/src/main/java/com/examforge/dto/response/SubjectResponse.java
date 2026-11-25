package com.examforge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code questionCount} is only filled in for admin endpoints. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubjectResponse(Long id, String name, String slug, String description, Long questionCount) {}
