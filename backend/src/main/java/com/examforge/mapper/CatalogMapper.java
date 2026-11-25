package com.examforge.mapper;

import com.examforge.dto.response.CategoryResponse;
import com.examforge.dto.response.SubjectResponse;
import com.examforge.entity.ExamCategory;
import com.examforge.entity.Subject;

public final class CatalogMapper {
    private CatalogMapper() {}

    public static SubjectResponse toResponse(Subject s) {
        return new SubjectResponse(s.getId(), s.getName(), s.getSlug(), s.getDescription(), null);
    }

    public static SubjectResponse toResponse(Subject s, long questionCount) {
        return new SubjectResponse(s.getId(), s.getName(), s.getSlug(), s.getDescription(), questionCount);
    }

    public static CategoryResponse toResponse(ExamCategory c, long publishedExamCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getIcon(),
                c.getDisplayOrder(), c.isActive(),
                c.getSubjects().stream().map(CatalogMapper::toResponse).toList(), publishedExamCount);
    }
}
