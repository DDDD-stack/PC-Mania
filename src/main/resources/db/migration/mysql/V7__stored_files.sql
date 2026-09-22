-- Uploaded files live in the database rather than on disk; see the Postgres copy of this
-- migration for why. file_key doubles as the path the file is served at.
CREATE TABLE stored_file (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key      VARCHAR(200) NOT NULL UNIQUE,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    INT          NOT NULL,
    width_px      INT,
    height_px     INT,
    label         VARCHAR(200),
    data          LONGBLOB     NOT NULL,
    created_at    DATETIME     NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
