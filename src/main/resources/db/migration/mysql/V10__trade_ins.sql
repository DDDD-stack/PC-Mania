-- Kept in step with postgresql/V10: "Nderro" trade-ins.
ALTER TABLE product
    ADD COLUMN trade_eligible      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_trade_value_lek INT NULL;

CREATE TABLE trade_request (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_number          VARCHAR(20)   NOT NULL UNIQUE,
    product_id              BIGINT        NULL,
    product_title_snapshot  VARCHAR(200)  NOT NULL,
    customer_name           VARCHAR(120)  NOT NULL,
    contact_method          VARCHAR(10)   NOT NULL,
    customer_phone          VARCHAR(30)   NULL,
    customer_email          VARCHAR(160)  NULL,
    item_type               VARCHAR(10)   NOT NULL,
    manufacturer            VARCHAR(80)   NOT NULL,
    model                   VARCHAR(120)  NOT NULL,
    extra_notes             VARCHAR(1000) NULL,
    media_filename          VARCHAR(200)  NULL,
    media_type              VARCHAR(10)   NULL,
    whatsapp_instead        BOOLEAN       NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20)   NOT NULL,
    quoted_value_lek        INT           NULL,
    quote_notes             VARCHAR(1000) NULL,
    decline_reason          VARCHAR(1000) NULL,
    quoted_at               DATETIME      NULL,
    quote_expires_at        DATETIME      NULL,
    quote_emailed_at        DATETIME      NULL,
    closed_at               DATETIME      NULL,
    created_at              DATETIME      NOT NULL,
    order_id                BIGINT        NULL,
    stock_product_id        BIGINT        NULL,
    INDEX ix_trade_status (status, created_at),
    CONSTRAINT fk_trade_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE SET NULL,
    CONSTRAINT fk_trade_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_trade_stock FOREIGN KEY (stock_product_id) REFERENCES product (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE trade_sequence (
    seq_year  INT PRIMARY KEY,
    last_no   INT NOT NULL
) ENGINE = InnoDB;

ALTER TABLE orders
    ADD COLUMN trade_request_id BIGINT NULL,
    ADD COLUMN trade_credit_lek INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_order_trade FOREIGN KEY (trade_request_id) REFERENCES trade_request (id) ON DELETE SET NULL;
