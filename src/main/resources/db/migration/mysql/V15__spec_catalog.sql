-- Kept in step with postgresql/V15: the generic autofill catalogue.
CREATE TABLE spec_catalog_entry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_slug   VARCHAR(40)   NOT NULL,
    name            VARCHAR(160)  NOT NULL,
    aliases         VARCHAR(500)  NULL,
    spec_json       JSON          NOT NULL,
    verified        BOOLEAN       NOT NULL DEFAULT FALSE,
    gpu_catalog_id  BIGINT        NULL UNIQUE,
    CONSTRAINT fk_spec_catalog_gpu FOREIGN KEY (gpu_catalog_id) REFERENCES gpu_catalog (id) ON DELETE CASCADE,
    INDEX ix_spec_catalog_category (category_slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
