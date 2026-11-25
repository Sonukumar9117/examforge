package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Score of a finished attempt. Calculated only on the server, inside the submit transaction. */
@Entity
@Table(name = "results")
@Getter
@Setter
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private ExamAttempt attempt;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "unanswered_count", nullable = false)
    private int unansweredCount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal accuracy;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal percentage;

    @Column(name = "time_taken_seconds", nullable = false)
    private int timeTakenSeconds;

    private Boolean passed;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultSubjectScore> subjectScores = new ArrayList<>();

    protected Result() {}

    public Result(ExamAttempt attempt) {
        this.attempt = attempt;
        this.examId = attempt.getExam().getId();
        this.userId = attempt.getUserId();
    }

}
