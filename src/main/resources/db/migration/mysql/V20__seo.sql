-- Kept in step with postgresql/V20: category and product search fields, and the product slug history.
ALTER TABLE category ADD COLUMN meta_title VARCHAR(120) NULL;
ALTER TABLE category ADD COLUMN meta_description VARCHAR(300) NULL;
ALTER TABLE category ADD COLUMN intro_html TEXT NULL;

ALTER TABLE product ADD COLUMN meta_description VARCHAR(300) NULL;

CREATE TABLE product_slug_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    old_slug    VARCHAR(220) NOT NULL UNIQUE,
    product_id  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slug_history_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    INDEX ix_slug_history_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

UPDATE category SET meta_title = 'Procesorë të Rinj dhe të Përdorur në Tiranë | PCMania' WHERE slug = 'procesore' AND meta_title IS NULL;
UPDATE category SET meta_title = 'Disqe SSD dhe HDD të Rinj e të Përdorur në Tiranë | PCMania' WHERE slug = 'disqe' AND meta_title IS NULL;
UPDATE category SET meta_title = 'Furnizues Energjie (PSU) të Rinj e të Përdorur në Tiranë' WHERE slug = 'furnizues-energjie' AND meta_title IS NULL;
UPDATE category SET meta_title = 'Monitorë të Rinj dhe të Përdorur në Tiranë | PCMania' WHERE slug = 'monitore' AND meta_title IS NULL;
UPDATE category SET meta_title = 'PC Gaming të Gatshëm në Tiranë | PCMania' WHERE slug = 'pc-te-gatshem' AND meta_title IS NULL;

SET @taken := (SELECT COUNT(*) FROM product WHERE slug = 'motherboard-msi-a520m-a-pro');

INSERT INTO product_slug_history (old_slug, product_id)
SELECT p.slug, p.id FROM product p WHERE p.slug = 'motherboard-msi-a520m-tomawhok' AND @taken = 0;

UPDATE product SET slug = 'motherboard-msi-a520m-a-pro' WHERE slug = 'motherboard-msi-a520m-tomawhok' AND @taken = 0;
