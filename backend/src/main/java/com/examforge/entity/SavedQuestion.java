package com.examforge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A question a student bookmarked from a result review, with an optional personal note. */
@Entity
@Table(name = "saved_questions")
@Getter
@Setter
public class SavedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SavedQuestion() {}

    public SavedQuestion(Long userId, Question question, String note) {
        this.userId = userId;
        this.question = question;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }


}
