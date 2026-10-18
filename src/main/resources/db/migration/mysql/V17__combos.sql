-- Kept in step with postgresql/V17: combo deals and the bundle an order came from.
CREATE TABLE combo (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug             VARCHAR(160)  NOT NULL UNIQUE,
    title_sq         VARCHAR(200)  NOT NULL,
    subtitle_sq      VARCHAR(300)  NULL,
    combo_price_lek  INT           NOT NULL,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order       INT           NOT NULL DEFAULT 0,
    starts_at        DATETIME      NULL,
    ends_at          DATETIME      NULL,
    created_at       DATETIME      NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE combo_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id    BIGINT  NOT NULL,
    product_id  BIGINT  NOT NULL,
    quantity    INT     NOT NULL DEFAULT 1,
    CONSTRAINT fk_combo_item_combo FOREIGN KEY (combo_id) REFERENCES combo (id) ON DELETE CASCADE,
    CONSTRAINT fk_combo_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT uq_combo_item UNIQUE (combo_id, product_id),
    INDEX ix_combo_item_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE orders
    ADD COLUMN combo_id BIGINT NULL,
    ADD COLUMN combo_title_snapshot VARCHAR(200) NULL;
