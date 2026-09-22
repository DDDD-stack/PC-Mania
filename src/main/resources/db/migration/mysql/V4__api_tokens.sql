-- Bearer tokens for the mobile admin app. Only a SHA-256 hash of each token is stored.
CREATE TABLE api_token (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id  BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL UNIQUE,
    device_name    VARCHAR(100),
    created_at     DATETIME     NOT NULL,
    last_used_at   DATETIME     NOT NULL,
    CONSTRAINT fk_token_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
