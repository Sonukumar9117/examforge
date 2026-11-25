package com.examforge.dto.response;

import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String icon,
        int displayOrder,
        boolean active,
        List<SubjectResponse> subjects,
        long publishedExamCount) {}
