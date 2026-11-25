package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A question placed in an exam, with its order and (possibly overridden) marking scheme. */
@Entity
@Table(name = "exam_questions")
@Getter
@Setter
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal marks;

    @Column(name = "negative_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal negativeMarks;

    protected ExamQuestion() {}

    public ExamQuestion(Exam exam, Question question, int displayOrder, BigDecimal marks, BigDecimal negativeMarks) {
        this.exam = exam;
        this.question = question;
        this.displayOrder = displayOrder;
        this.marks = marks;
        this.negativeMarks = negativeMarks;
    }

}
