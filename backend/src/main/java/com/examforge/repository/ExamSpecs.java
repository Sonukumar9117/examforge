package com.examforge.repository;

import com.examforge.entity.Exam;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dynamic filters for exam lists (admin and student explore). Every argument is optional. */
public final class ExamSpecs {
    private ExamSpecs() {}

    public static Specification<Exam> filter(String text, Long categoryId, String categorySlug, ExamStatus status,
                                             ExamType type, Difficulty difficulty, Long subjectId) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("description")), like)));
            }
            if (categoryId != null) p.add(cb.equal(root.get("category").get("id"), categoryId));
            if (categorySlug != null && !categorySlug.isBlank()) p.add(cb.equal(root.get("category").get("slug"), categorySlug));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (difficulty != null) p.add(cb.equal(root.get("difficulty"), difficulty));
            if (subjectId != null) {
                // exams that contain at least one question of this subject
                var sub = query.subquery(Long.class);
                var eq = sub.from(com.examforge.entity.ExamQuestion.class);
                sub.select(eq.get("id")).where(
                        cb.equal(eq.get("exam"), root),
                        cb.equal(eq.get("question").get("subject").get("id"), subjectId));
                p.add(cb.exists(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Published exams whose availability window has not ended yet. */
    public static Specification<Exam> visibleToStudents(java.time.Instant now) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ExamStatus.PUBLISHED),
                cb.or(cb.isNull(root.get("endsAt")), cb.greaterThan(root.get("endsAt"), now)));
    }
}
