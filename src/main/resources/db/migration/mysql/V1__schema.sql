CREATE TABLE category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_sq     VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    icon_class  VARCHAR(60)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE brand (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(80) NOT NULL UNIQUE,
    slug  VARCHAR(80) NOT NULL UNIQUE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    brand_id            BIGINT,
    model               VARCHAR(120),
    category_slug       VARCHAR(100) NOT NULL,
    slug                VARCHAR(220) NOT NULL UNIQUE,
    item_condition      VARCHAR(20)  NOT NULL,
    price_lek           INT          NOT NULL,
    cost_lek            INT          NOT NULL DEFAULT 0,
    quantity            INT          NOT NULL DEFAULT 1,
    status              VARCHAR(20)  NOT NULL,
    short_description   VARCHAR(500),
    full_description    TEXT,
    warranty_days       INT,
    test_notes          TEXT,
    is_mining_free      BOOLEAN      NOT NULL DEFAULT FALSE,
    transport_included  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME     NOT NULL,
    listed_at           DATETIME,
    sold_at             DATETIME,
    view_count          INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brand (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_slug) REFERENCES category (slug) ON UPDATE CASCADE,
    INDEX ix_product_status_listed (status, listed_at),
    INDEX ix_product_category_status (category_slug, status),
    INDEX ix_product_price (price_lek)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_spec (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    spec_key    VARCHAR(80)  NOT NULL,
    spec_value  VARCHAR(160) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_spec_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    INDEX ix_spec_key_value (spec_key, spec_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    filename    VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number     VARCHAR(20)  NOT NULL UNIQUE,
    customer_name    VARCHAR(120) NOT NULL,
    customer_phone   VARCHAR(30)  NOT NULL,
    customer_email   VARCHAR(160),
    city             VARCHAR(80)  NOT NULL,
    address          VARCHAR(255),
    customer_notes   VARCHAR(1000),
    delivery_method  VARCHAR(20)  NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    subtotal_lek     INT          NOT NULL,
    shipping_lek     INT          NOT NULL,
    total_lek        INT          NOT NULL,
    admin_notes      TEXT,
    created_at       DATETIME     NOT NULL,
    delivered_at     DATETIME,
    INDEX ix_orders_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_item (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id             BIGINT       NOT NULL,
    product_id           BIGINT,
    title_snapshot       VARCHAR(200) NOT NULL,
    price_lek_snapshot   INT          NOT NULL,
    cost_lek_snapshot    INT          NOT NULL,
    quantity             INT          NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Per-year counter backing human-readable order numbers (PM-2026-0001)
CREATE TABLE order_sequence (
    seq_year  INT PRIMARY KEY,
    last_no   INT NOT NULL
) ENGINE = InnoDB;

CREATE TABLE build_request (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name     VARCHAR(120) NOT NULL,
    customer_phone    VARCHAR(30)  NOT NULL,
    budget_lek        INT          NOT NULL,
    use_case          VARCHAR(20)  NOT NULL,
    notes             TEXT,
    status            VARCHAR(20)  NOT NULL,
    quoted_total_lek  INT,
    admin_notes       TEXT,
    created_at        DATETIME     NOT NULL,
    INDEX ix_build_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE admin_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(60)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
