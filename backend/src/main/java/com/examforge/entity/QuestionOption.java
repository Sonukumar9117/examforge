package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "question_options")
@Getter
@Setter
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 5)
    private String label;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    /** Never sent to students before results are released. */
    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected QuestionOption() {}

    public QuestionOption(String optionText, boolean correct) {
        this.optionText = optionText;
        this.correct = correct;
    }

}
