-- ExamForge V1: users, roles/permissions, profiles, tokens, audit log.
-- utf8mb4_0900_ai_ci makes username/email uniqueness case-insensitive.

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(30)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(100) NULL COMMENT 'NULL for Google-only accounts',
    full_name             VARCHAR(100) NOT NULL,
    avatar_url            VARCHAR(500) NULL,
    google_id             VARCHAR(64)  NULL,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    last_login_at         DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id),
    INDEX idx_users_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_profiles (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    target_category_id BIGINT       NULL COMMENT 'FK to exam_categories added in V2',
    bio                VARCHAR(500) NULL,
    phone              VARCHAR(20)  NULL,
    theme_preference   VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    current_streak     INT          NOT NULL DEFAULT 0,
    longest_streak     INT          NOT NULL DEFAULT 0,
    last_active_date   DATE         NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL COMMENT 'SHA-256 hex of the raw token',
    family_id   VARCHAR(36)  NOT NULL COMMENT 'all rotations of one login share a family',
    remember_me BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6)  NULL,
    user_agent  VARCHAR(255) NULL,
    ip_address  VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    type       VARCHAR(30) NOT NULL COMMENT 'EMAIL_VERIFICATION | PASSWORD_RESET',
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_auth_tokens_user_type (user_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    actor_id       BIGINT      NULL,
    actor_username VARCHAR(30) NULL,
    action         VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(60) NULL,
    entity_id      VARCHAR(64) NULL,
    details        TEXT        NULL,
    ip_address     VARCHAR(45) NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_audit_logs_created_at (created_at),
    INDEX idx_audit_logs_actor (actor_id),
    INDEX idx_audit_logs_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Seed roles and permissions
INSERT INTO roles (name, description) VALUES
    ('ROLE_STUDENT', 'Default role for every registered user'),
    ('ROLE_ADMIN', 'Full administrative access');

INSERT INTO permissions (name, description) VALUES
    ('EXAM_TAKE', 'Start and submit exam attempts'),
    ('EXAM_MANAGE', 'Create, edit, publish and delete exams'),
    ('QUESTION_MANAGE', 'Manage the question bank'),
    ('CATEGORY_MANAGE', 'Manage exam categories and subjects'),
    ('ROOM_MANAGE', 'Schedule and control live exam rooms'),
    ('USER_MANAGE', 'Manage users and roles'),
    ('REPORT_VIEW', 'View analytics and export reports'),
    ('AUDIT_VIEW', 'View audit logs');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'ROLE_STUDENT' AND p.name = 'EXAM_TAKE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.name = 'ROLE_ADMIN';
