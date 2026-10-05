-- Kept in step with postgresql/V11: the GPU catalogue the assistant and the admin autofill work from.
CREATE TABLE gpu_catalog (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug                VARCHAR(80)   NOT NULL UNIQUE,
    name                VARCHAR(120)  NOT NULL,
    vendor              VARCHAR(10)   NOT NULL,
    aliases             VARCHAR(300)  NULL,
    release_year        INT           NULL,
    architecture        VARCHAR(60)   NULL,
    vram_gb             INT           NULL,
    memory_type         VARCHAR(20)   NULL,
    memory_bus_bits     INT           NULL,
    tdp_watts           INT           NULL,
    psu_min_watts       INT           NULL,
    pcie_connectors     VARCHAR(40)   NULL,
    length_mm           INT           NULL,
    tier                INT           NULL,
    supports_dlss       VARCHAR(10)   NULL,
    frame_generation    BOOLEAN       NOT NULL DEFAULT FALSE,
    supports_fsr        VARCHAR(10)   NULL,
    ray_tracing         BOOLEAN       NOT NULL DEFAULT FALSE,
    driver_status       VARCHAR(10)   NULL,
    mining_risk         VARCHAR(10)   NULL,
    fps_esports_1080p   INT           NULL,
    fps_aaa_1080p       INT           NULL,
    fps_aaa_1440p       INT           NULL,
    notes_sq            VARCHAR(1000) NULL,
    INDEX ix_gpu_catalog_tier (tier)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE product
    ADD COLUMN gpu_model_id BIGINT NULL,
    ADD CONSTRAINT fk_product_gpu_model FOREIGN KEY (gpu_model_id) REFERENCES gpu_catalog (id) ON DELETE SET NULL;
