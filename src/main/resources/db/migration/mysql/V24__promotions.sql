CREATE TABLE promotion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120)  NOT NULL,
    headline        VARCHAR(200)  NULL,
    discount_type   VARCHAR(16)   NOT NULL,
    discount_value  INT           NOT NULL,
    starts_at       DATETIME      NOT NULL,
    ends_at         DATETIME      NOT NULL,
    theme           VARCHAR(16)   NOT NULL DEFAULT 'FIRE',
    show_banner     BOOLEAN       NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME      NOT NULL,
    INDEX ix_promotion_window (ends_at, starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE promotion_product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    promotion_id    BIGINT  NOT NULL,
    product_id      BIGINT  NOT NULL,
    sale_price_lek  INT     NULL,
    CONSTRAINT fk_promotion_product_promotion FOREIGN KEY (promotion_id) REFERENCES promotion (id) ON DELETE CASCADE,
    CONSTRAINT fk_promotion_product_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_promotion_product UNIQUE (promotion_id, product_id),
    INDEX ix_promotion_product_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
