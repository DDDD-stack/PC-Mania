-- Kept in step with postgresql/V13: provider-agnostic assistant, lead sources, provider usage counters.
ALTER TABLE chat_session ADD COLUMN provider_used VARCHAR(20) NULL;
ALTER TABLE chat_message ADD COLUMN provider_used VARCHAR(20) NULL;

ALTER TABLE chat_lead
    MODIFY COLUMN session_id BIGINT NULL,
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'CHAT';

CREATE TABLE provider_usage (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider         VARCHAR(30)  NOT NULL,
    day              DATE         NOT NULL,
    request_count    INT          NOT NULL DEFAULT 0,
    error_count      INT          NOT NULL DEFAULT 0,
    rate_limit_hits  INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uq_provider_usage (provider, day)
) ENGINE = InnoDB;
