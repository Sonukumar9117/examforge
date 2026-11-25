package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** The student's state for one question in an attempt: chosen options, visited, marked for review. */
@Entity
@Table(name = "attempt_answers")
@Getter
@Setter
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @ElementCollection
    @CollectionTable(name = "attempt_answer_options", joinColumns = @JoinColumn(name = "answer_id"))
    @Column(name = "option_id", nullable = false)
    private Set<Long> selectedOptionIds = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean visited;

    @Column(name = "marked_for_review", nullable = false)
    private boolean markedForReview;

    @Column(name = "client_seq", nullable = false)
    private long clientSeq;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "marks_awarded", precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttemptAnswer() {}

    public AttemptAnswer(ExamAttempt attempt, Long questionId) {
        this.attempt = attempt;
        this.questionId = questionId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isAnswered() {
        return !selectedOptionIds.isEmpty();
    }


}
