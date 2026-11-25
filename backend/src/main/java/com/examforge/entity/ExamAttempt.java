package com.examforge.entity;

import com.examforge.entity.enums.AttemptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One student's sitting of an exam. Start time and deadline are always set by the server. */
@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "last_saved_at")
    private Instant lastSavedAt;

    /** JSON: {"questions":[questionId…], "options":{questionId:[optionId…]}} — the order this student sees. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String layout;

    @Version
    private long version;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttemptAnswer> answers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExamAttempt() {}

    public ExamAttempt(Exam exam, Long userId, int attemptNo, Instant startedAt, Instant deadlineAt, String layout) {
        this.exam = exam;
        this.userId = userId;
        this.attemptNo = attemptNo;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.layout = layout;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

}
