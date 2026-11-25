package com.examforge.entity;

import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.QuestionStatus;
import com.examforge.entity.enums.QuestionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A reusable question in the question bank. Exams reference questions through {@link ExamQuestion}. */
@Entity
@Table(name = "questions")
@Getter
@Setter
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private QuestionType type = QuestionType.MCQ;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(nullable = false, length = 10)
    private String language = "EN";

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks = BigDecimal.ONE;

    @Column(name = "negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal negativeMarks = BigDecimal.ZERO;

    @Column(length = 255)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag", length = 50, nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Question() {}

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Replaces all options, assigning labels A, B, C … in order. */
    public void replaceOptions(List<QuestionOption> newOptions) {
        options.clear();
        for (int i = 0; i < newOptions.size(); i++) {
            QuestionOption o = newOptions.get(i);
            o.setQuestion(this);
            o.setLabel(String.valueOf((char) ('A' + i)));
            o.setDisplayOrder(i);
            options.add(o);
        }
    }

}
