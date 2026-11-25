-- ExamForge V2: exam categories, subjects, question bank, exams.

CREATE TABLE exam_categories (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)  NOT NULL,
    slug          VARCHAR(120)  NOT NULL,
    description   VARCHAR(1000) NULL,
    icon          VARCHAR(50)   NULL COMMENT 'lucide icon name used by the clients',
    display_order INT           NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_categories_name UNIQUE (name),
    CONSTRAINT uk_exam_categories_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subjects (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subjects_name UNIQUE (name),
    CONSTRAINT uk_subjects_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE category_subjects (
    category_id BIGINT NOT NULL,
    subject_id  BIGINT NOT NULL,
    PRIMARY KEY (category_id, subject_id),
    CONSTRAINT fk_category_subjects_category FOREIGN KEY (category_id) REFERENCES exam_categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE questions (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    subject_id     BIGINT        NOT NULL,
    topic          VARCHAR(100)  NULL,
    type           VARCHAR(20)   NOT NULL COMMENT 'MCQ | MULTI_SELECT | TRUE_FALSE',
    question_text  TEXT          NOT NULL,
    image_url      VARCHAR(500)  NULL,
    explanation    TEXT          NULL,
    difficulty     VARCHAR(10)   NOT NULL COMMENT 'EASY | MEDIUM | HARD',
    language       VARCHAR(10)   NOT NULL DEFAULT 'EN',
    marks          DECIMAL(6, 2) NOT NULL DEFAULT 1.00,
    negative_marks DECIMAL(6, 2) NOT NULL DEFAULT 0.00,
    source         VARCHAR(255)  NULL COMMENT 'where the question came from; never claim official papers for samples',
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | APPROVED',
    created_by     BIGINT        NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_questions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_questions_subject (subject_id),
    INDEX idx_questions_status (status),
    INDEX idx_questions_difficulty (difficulty),
    INDEX idx_questions_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_options (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    question_id   BIGINT      NOT NULL,
    label         VARCHAR(5)  NOT NULL COMMENT 'A, B, C …',
    option_text   TEXT        NOT NULL,
    is_correct    BOOLEAN     NOT NULL DEFAULT FALSE,
    display_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    INDEX idx_question_options_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_tags (
    question_id BIGINT      NOT NULL,
    tag         VARCHAR(50) NOT NULL,
    PRIMARY KEY (question_id, tag),
    CONSTRAINT fk_question_tags_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    INDEX idx_question_tags_tag (tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE exams (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    category_id      BIGINT       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    slug             VARCHAR(220) NOT NULL,
    description      TEXT         NULL,
    instructions     TEXT         NULL,
    type             VARCHAR(20)  NOT NULL COMMENT 'PRACTICE | MOCK | LIVE',
    difficulty       VARCHAR(10)  NOT NULL COMMENT 'EASY | MEDIUM | HARD',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | PUBLISHED | ARCHIVED',
    duration_minutes INT          NOT NULL,
    pass_marks       DECIMAL(8, 2) NULL,
    starts_at        DATETIME(6)  NULL,
    ends_at          DATETIME(6)  NULL,
    max_attempts     INT          NULL COMMENT 'NULL = unlimited',
    shuffle_questions BOOLEAN     NOT NULL DEFAULT FALSE,
    shuffle_options  BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_navigation BOOLEAN      NOT NULL DEFAULT TRUE,
    result_release   VARCHAR(20)  NOT NULL DEFAULT 'IMMEDIATE' COMMENT 'IMMEDIATE | AFTER_END | MANUAL',
    created_by       BIGINT       NULL,
    published_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exams_slug UNIQUE (slug),
    CONSTRAINT fk_exams_category FOREIGN KEY (category_id) REFERENCES exam_categories (id),
    CONSTRAINT fk_exams_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_exams_category_status (category_id, status),
    INDEX idx_exams_status_starts (status, starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE exam_questions (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    exam_id        BIGINT        NOT NULL,
    question_id    BIGINT        NOT NULL,
    display_order  INT           NOT NULL,
    marks          DECIMAL(6, 2) NOT NULL COMMENT 'copied from the question, can be overridden per exam',
    negative_marks DECIMAL(6, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exam_questions UNIQUE (exam_id, question_id),
    CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id) REFERENCES questions (id),
    INDEX idx_exam_questions_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_target_category
        FOREIGN KEY (target_category_id) REFERENCES exam_categories (id) ON DELETE SET NULL;

-- Initial categories and subjects (editable by admins afterwards)
INSERT INTO exam_categories (name, slug, description, icon, display_order, active, created_at, updated_at) VALUES
    ('UPSC Civil Services', 'upsc-civil-services', 'Prelims GS Paper I & CSAT practice for the Civil Services Examination.', 'Landmark', 1, TRUE, NOW(6), NOW(6)),
    ('SSC CGL & CHSL', 'ssc-cgl-chsl', 'Staff Selection Commission tier-1 style tests.', 'ClipboardList', 2, TRUE, NOW(6), NOW(6)),
    ('Banking (IBPS / SBI PO & Clerk)', 'banking', 'Prelims-style tests for IBPS PO, SBI PO and Clerk exams.', 'Banknote', 3, TRUE, NOW(6), NOW(6)),
    ('Railways (RRB NTPC & Group D)', 'railways', 'CBT practice for RRB NTPC and Group D.', 'TrainFront', 4, TRUE, NOW(6), NOW(6)),
    ('State PCS', 'state-pcs', 'State public service commission preliminary exams.', 'Map', 5, TRUE, NOW(6), NOW(6)),
    ('GATE', 'gate', 'Graduate Aptitude Test in Engineering practice.', 'Cpu', 6, TRUE, NOW(6), NOW(6)),
    ('JEE & NEET', 'jee-neet', 'Physics, Chemistry, Mathematics and Biology practice.', 'FlaskConical', 7, TRUE, NOW(6), NOW(6)),
    ('General Knowledge & Current Affairs', 'gk-current-affairs', 'Daily GK and current-affairs quizzes.', 'Newspaper', 8, TRUE, NOW(6), NOW(6)),
    ('Custom Practice', 'custom-practice', 'Mixed and topic-wise custom practice tests.', 'Sparkles', 9, TRUE, NOW(6), NOW(6));

INSERT INTO subjects (name, slug, created_at, updated_at) VALUES
    ('General Studies', 'general-studies', NOW(6), NOW(6)),
    ('History', 'history', NOW(6), NOW(6)),
    ('Geography', 'geography', NOW(6), NOW(6)),
    ('Indian Polity', 'indian-polity', NOW(6), NOW(6)),
    ('Economy', 'economy', NOW(6), NOW(6)),
    ('General Science', 'general-science', NOW(6), NOW(6)),
    ('Current Affairs', 'current-affairs', NOW(6), NOW(6)),
    ('Quantitative Aptitude', 'quantitative-aptitude', NOW(6), NOW(6)),
    ('Reasoning', 'reasoning', NOW(6), NOW(6)),
    ('English Language', 'english-language', NOW(6), NOW(6)),
    ('Computer Awareness', 'computer-awareness', NOW(6), NOW(6)),
    ('Physics', 'physics', NOW(6), NOW(6)),
    ('Chemistry', 'chemistry', NOW(6), NOW(6)),
    ('Mathematics', 'mathematics', NOW(6), NOW(6)),
    ('Biology', 'biology', NOW(6), NOW(6)),
    ('Engineering Core', 'engineering-core', NOW(6), NOW(6));

INSERT INTO category_subjects (category_id, subject_id)
SELECT c.id, s.id FROM exam_categories c JOIN subjects s
WHERE (c.slug = 'upsc-civil-services' AND s.slug IN ('general-studies','history','geography','indian-polity','economy','general-science','current-affairs','reasoning'))
   OR (c.slug = 'ssc-cgl-chsl'       AND s.slug IN ('general-studies','quantitative-aptitude','reasoning','english-language','current-affairs'))
   OR (c.slug = 'banking'            AND s.slug IN ('quantitative-aptitude','reasoning','english-language','computer-awareness','economy','current-affairs'))
   OR (c.slug = 'railways'           AND s.slug IN ('general-science','quantitative-aptitude','reasoning','general-studies','current-affairs'))
   OR (c.slug = 'state-pcs'          AND s.slug IN ('general-studies','history','geography','indian-polity','economy','current-affairs'))
   OR (c.slug = 'gate'               AND s.slug IN ('engineering-core','mathematics','reasoning'))
   OR (c.slug = 'jee-neet'           AND s.slug IN ('physics','chemistry','mathematics','biology'))
   OR (c.slug = 'gk-current-affairs' AND s.slug IN ('general-studies','current-affairs','history','geography'))
   OR (c.slug = 'custom-practice'    AND s.slug IN ('general-studies','quantitative-aptitude','reasoning','english-language'));
