package com.examforge.entity;

import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import com.examforge.entity.enums.ResultRelease;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exams")
@Getter
@Setter
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExamCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private ExamType type = ExamType.MOCK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private ExamStatus status = ExamStatus.DRAFT;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "pass_marks", precision = 8, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "allow_navigation", nullable = false)
    private boolean allowNavigation = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_release", nullable = false, columnDefinition = "VARCHAR(20)")
    private ResultRelease resultRelease = ResultRelease.IMMEDIATE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "results_released_at")
    private Instant resultsReleasedAt;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ExamQuestion> questions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Exam() {}

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Sum of the marks of all questions in this exam. */
    public BigDecimal totalMarks() {
        return questions.stream().map(ExamQuestion::getMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
