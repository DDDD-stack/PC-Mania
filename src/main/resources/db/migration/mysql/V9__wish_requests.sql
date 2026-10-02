-- Kept in step with postgresql/V9: what customers ask the shop to bring in.
CREATE TABLE wish_request (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name   VARCHAR(120)  NOT NULL,
    customer_phone  VARCHAR(30)   NOT NULL,
    item            VARCHAR(200)  NOT NULL,
    max_price_lek   INT,
    item_condition  VARCHAR(20),
    notes           VARCHAR(1000),
    status          VARCHAR(20)   NOT NULL,
    admin_notes     TEXT,
    created_at      DATETIME      NOT NULL,
    INDEX ix_wish_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
