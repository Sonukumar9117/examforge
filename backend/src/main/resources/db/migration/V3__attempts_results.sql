-- ExamForge V3: exam attempts, saved answers, results, saved questions.

ALTER TABLE exams
    ADD COLUMN results_released_at DATETIME(6) NULL COMMENT 'set when an admin releases answers for MANUAL exams';

CREATE TABLE exam_attempts (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    exam_id      BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    attempt_no   INT         NOT NULL,
    status       VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS | SUBMITTED | AUTO_SUBMITTED',
    started_at   DATETIME(6) NOT NULL COMMENT 'server time',
    deadline_at  DATETIME(6) NOT NULL COMMENT 'server-enforced end: min(start + duration, exam end)',
    submitted_at DATETIME(6) NULL,
    last_saved_at DATETIME(6) NULL,
    layout       TEXT        NOT NULL COMMENT 'JSON: question order and option order shown to this student',
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_attempts UNIQUE (exam_id, user_id, attempt_no),
    CONSTRAINT fk_exam_attempts_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_exam_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_exam_attempts_user_status (user_id, status),
    INDEX idx_exam_attempts_status_deadline (status, deadline_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attempt_answers (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    attempt_id        BIGINT        NOT NULL,
    question_id       BIGINT        NOT NULL,
    visited           BOOLEAN       NOT NULL DEFAULT FALSE,
    marked_for_review BOOLEAN       NOT NULL DEFAULT FALSE,
    client_seq        BIGINT        NOT NULL DEFAULT 0 COMMENT 'last applied client change; older saves are ignored',
    answered_at       DATETIME(6)   NULL,
    is_correct        BOOLEAN       NULL COMMENT 'filled at submission',
    marks_awarded     DECIMAL(6, 2) NULL COMMENT 'filled at submission (negative for wrong answers)',
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attempt_answers UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_answers_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attempt_answer_options (
    answer_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    PRIMARY KEY (answer_id, option_id),
    CONSTRAINT fk_answer_options_answer FOREIGN KEY (answer_id) REFERENCES attempt_answers (id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_options_option FOREIGN KEY (option_id) REFERENCES question_options (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE results (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    attempt_id         BIGINT         NOT NULL,
    exam_id            BIGINT         NOT NULL,
    user_id            BIGINT         NOT NULL,
    score              DECIMAL(10, 2) NOT NULL,
    max_score          DECIMAL(10, 2) NOT NULL,
    correct_count      INT            NOT NULL,
    incorrect_count    INT            NOT NULL,
    unanswered_count   INT            NOT NULL,
    accuracy           DECIMAL(5, 2)  NOT NULL COMMENT 'correct / attempted * 100',
    percentage         DECIMAL(6, 2)  NOT NULL COMMENT 'score / max_score * 100 (can be negative)',
    time_taken_seconds INT            NOT NULL,
    passed             BOOLEAN        NULL COMMENT 'NULL when the exam has no pass mark',
    computed_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_results_attempt UNIQUE (attempt_id),
    CONSTRAINT fk_results_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_results_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_results_exam_score (exam_id, score),
    INDEX idx_results_user_computed (user_id, computed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE result_subject_scores (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    result_id        BIGINT         NOT NULL,
    subject_id       BIGINT         NOT NULL,
    score            DECIMAL(10, 2) NOT NULL,
    max_score        DECIMAL(10, 2) NOT NULL,
    correct_count    INT            NOT NULL,
    incorrect_count  INT            NOT NULL,
    unanswered_count INT            NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_result_subject UNIQUE (result_id, subject_id),
    CONSTRAINT fk_result_subject_result FOREIGN KEY (result_id) REFERENCES results (id) ON DELETE CASCADE,
    CONSTRAINT fk_result_subject_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE saved_questions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    question_id BIGINT       NOT NULL,
    note        VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_saved_questions UNIQUE (user_id, question_id),
    CONSTRAINT fk_saved_questions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_questions_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
