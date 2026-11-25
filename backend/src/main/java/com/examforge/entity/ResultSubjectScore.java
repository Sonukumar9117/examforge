package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "result_subject_scores")
@Getter
@Setter
public class ResultSubjectScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore = BigDecimal.ZERO;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "unanswered_count", nullable = false)
    private int unansweredCount;

    protected ResultSubjectScore() {}

    public ResultSubjectScore(Result result, Subject subject) {
        this.result = result;
        this.subject = subject;
    }

    public void add(BigDecimal marksAwarded, BigDecimal maxMarks, Boolean correct) {
        score = score.add(marksAwarded);
        maxScore = maxScore.add(maxMarks);
        if (correct == null) unansweredCount++;
        else if (correct) correctCount++;
        else incorrectCount++;
    }
}
