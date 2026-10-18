-- Kept in step with postgresql/V16: the prebuilt category and its component table.
INSERT INTO category (name_sq, slug, sort_order, icon_class, is_visible, is_out_of_stock, catalog_slug)
SELECT 'PC të gatshëm', 'pc-te-gatshem', n.next_order, 'bi-pc-display', TRUE, FALSE, 'prebuilt'
FROM (SELECT COALESCE(MAX(sort_order), 0) + 1 AS next_order FROM category) n
WHERE NOT EXISTS (SELECT 1 FROM category WHERE catalog_slug = 'prebuilt' OR slug = 'pc-te-gatshem');

CREATE TABLE prebuilt_spec (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id          BIGINT        NOT NULL UNIQUE,
    cpu_name            VARCHAR(120)  NULL,
    cpu_cores           INT           NULL,
    cpu_threads         INT           NULL,
    gpu_catalog_id      BIGINT        NULL,
    gpu_name_override   VARCHAR(120)  NULL,
    ram_gb              INT           NULL,
    ram_speed_mhz       INT           NULL,
    ram_config          VARCHAR(40)   NULL,
    storage_primary     VARCHAR(120)  NULL,
    storage_secondary   VARCHAR(120)  NULL,
    psu_watts           INT           NULL,
    psu_brand           VARCHAR(80)   NULL,
    motherboard_model   VARCHAR(120)  NULL,
    case_model          VARCHAR(120)  NULL,
    os_installed        BOOLEAN       NOT NULL DEFAULT FALSE,
    os_name             VARCHAR(80)   NULL,
    load_temp_cpu_c     INT           NULL,
    load_temp_gpu_c     INT           NULL,
    tested_at           DATE          NULL,
    notes_sq            TEXT          NULL,
    CONSTRAINT fk_prebuilt_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT fk_prebuilt_gpu FOREIGN KEY (gpu_catalog_id) REFERENCES spec_catalog_entry (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
