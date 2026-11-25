package com.examforge.repository;

import com.examforge.entity.Question;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dynamic filters for the question bank search. Every argument is optional. */
public final class QuestionSpecs {
    private QuestionSpecs() {}

    public static Specification<Question> filter(String text, Long subjectId, Difficulty difficulty,
                                                 QuestionType type, QuestionStatus status, String tag) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("questionText")), like),
                        cb.like(cb.lower(root.get("topic")), like)));
            }
            if (subjectId != null) p.add(cb.equal(root.get("subject").get("id"), subjectId));
            if (difficulty != null) p.add(cb.equal(root.get("difficulty"), difficulty));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (tag != null && !tag.isBlank()) {
                p.add(cb.isMember(tag.trim().toLowerCase(Locale.ROOT), root.<java.util.Collection<String>>get("tags")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
