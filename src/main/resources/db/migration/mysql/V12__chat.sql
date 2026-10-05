-- Kept in step with postgresql/V12: the customer assistant's sessions, messages, leads and spend.
CREATE TABLE chat_session (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token    VARCHAR(64)  NOT NULL UNIQUE,
    started_at       DATETIME     NOT NULL,
    last_message_at  DATETIME     NOT NULL,
    message_count    INT          NOT NULL DEFAULT 0,
    lead_captured    BOOLEAN      NOT NULL DEFAULT FALSE,
    INDEX ix_chat_session_last (last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    role            VARCHAR(10)  NOT NULL,
    content         TEXT         NOT NULL,
    tool_calls_json TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    INDEX ix_chat_message_session (session_id, id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chat_lead (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT        NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    phone         VARCHAR(30)   NOT NULL,
    wanted_item   VARCHAR(200)  NOT NULL,
    budget_lek    INT           NULL,
    psu_watts     INT           NULL,
    notes         VARCHAR(1000) NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME      NOT NULL,
    INDEX ix_chat_lead_status (status, created_at),
    CONSTRAINT fk_chat_lead_session FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chat_usage (
    month               VARCHAR(7)  PRIMARY KEY,
    input_tokens        BIGINT      NOT NULL DEFAULT 0,
    output_tokens       BIGINT      NOT NULL DEFAULT 0,
    cache_read_tokens   BIGINT      NOT NULL DEFAULT 0,
    cache_write_tokens  BIGINT      NOT NULL DEFAULT 0,
    cost_micro_usd      BIGINT      NOT NULL DEFAULT 0,
    replies             INT         NOT NULL DEFAULT 0,
    updated_at          DATETIME    NOT NULL
) ENGINE = InnoDB;
