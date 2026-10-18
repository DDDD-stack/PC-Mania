-- Kept in step with postgresql/V14: category field templates, the catalogue key per category, and
-- the move of old GPU spec labels onto the template's keys.
ALTER TABLE category ADD COLUMN catalog_slug VARCHAR(40) NULL;

UPDATE category SET catalog_slug = 'gpu'         WHERE slug = 'karta-grafike';
UPDATE category SET catalog_slug = 'cpu'         WHERE slug = 'procesore';
UPDATE category SET catalog_slug = 'motherboard' WHERE slug = 'pllaka-ame';
UPDATE category SET catalog_slug = 'ram'         WHERE slug = 'memorie-ram';
UPDATE category SET catalog_slug = 'storage'     WHERE slug = 'disqe';
UPDATE category SET catalog_slug = 'psu'         WHERE slug = 'furnizues-energjie';
UPDATE category SET catalog_slug = 'case'        WHERE slug = 'kasa';
UPDATE category SET catalog_slug = 'cooling'     WHERE slug = 'ftohje';
UPDATE category SET catalog_slug = 'monitor'     WHERE slug = 'monitore';

CREATE TABLE category_field_template (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id      BIGINT        NOT NULL,
    field_key        VARCHAR(60)   NOT NULL,
    label_sq         VARCHAR(100)  NOT NULL,
    label_en         VARCHAR(100)  NULL,
    data_type        VARCHAR(10)   NOT NULL,
    unit             VARCHAR(20)   NULL,
    enum_options     VARCHAR(1000) NULL,
    is_required      BOOLEAN       NOT NULL DEFAULT FALSE,
    show_in_listing  BOOLEAN       NOT NULL DEFAULT FALSE,
    show_in_filters  BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INT           NOT NULL DEFAULT 0,
    CONSTRAINT fk_field_template_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT uq_field_template_key UNIQUE (category_id, field_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TEMPORARY TABLE seed_field_template (
    catalog_slug VARCHAR(40), k VARCHAR(60), sq VARCHAR(100), en VARCHAR(100), dt VARCHAR(10), unit VARCHAR(20),
    opts VARCHAR(1000), req BOOLEAN, list BOOLEAN, filt BOOLEAN, o INT
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO seed_field_template VALUES
    ('gpu', 'vendor',            'Prodhuesi i çipit',       'Chip vendor',        'ENUM',    NULL,  '["NVIDIA","AMD","Intel"]', FALSE, FALSE, TRUE,  1),
    ('gpu', 'vram_gb',           'Memoria (VRAM)',          'Memory (VRAM)',      'INTEGER', 'GB',  NULL,                      FALSE,  TRUE,  TRUE,  2),
    ('gpu', 'memory_type',       'Tipi i memories',         'Memory type',        'TEXT',    NULL,  NULL,                      FALSE, TRUE,  FALSE, 3),
    ('gpu', 'memory_bus_bits',   'Busi i memories',         'Memory bus',         'INTEGER', 'bit', NULL,                      FALSE, FALSE, FALSE, 4),
    ('gpu', 'tdp_watts',         'Konsumi (TDP)',           'Power (TDP)',        'INTEGER', 'W',   NULL,                      FALSE, FALSE, FALSE, 5),
    ('gpu', 'psu_min_watts',     'Furnizuesi minimal',      'Minimum PSU',        'INTEGER', 'W',   NULL,                      FALSE, FALSE, FALSE, 6),
    ('gpu', 'pcie_connectors',   'Konektorët e energjisë',  'Power connectors',   'TEXT',    NULL,  NULL,                      FALSE, FALSE, FALSE, 7),
    ('gpu', 'length_mm',         'Gjatësia',                'Length',             'INTEGER', 'mm',  NULL,                      FALSE, FALSE, FALSE, 8),
    ('gpu', 'supports_dlss',     'DLSS',                    'DLSS',               'ENUM',    NULL,  '["Jo","1","2","3","4"]',  FALSE, FALSE, FALSE, 9),
    ('gpu', 'frame_generation',  'Frame generation',        'Frame generation',   'BOOLEAN', NULL,  NULL,                      FALSE, FALSE, FALSE, 10),
    ('gpu', 'supports_fsr',      'FSR',                     'FSR',                'ENUM',    NULL,  '["Jo","1","2","3","4"]',  FALSE, FALSE, FALSE, 11),
    ('gpu', 'ray_tracing',       'Ray tracing',             'Ray tracing',        'BOOLEAN', NULL,  NULL,                      FALSE, FALSE, FALSE, 12),
    ('gpu', 'driver_status',     'Drajverët',               'Driver support',     'ENUM',    NULL,  '["Merr drajverë","Legacy","Pa drajverë të rinj"]', FALSE, FALSE, FALSE, 13),

    ('cpu', 'socket',              'Soketi',              'Socket',             'TEXT',    NULL,  NULL, FALSE,  TRUE,  TRUE,  1),
    ('cpu', 'generation',          'Gjenerata',           'Generation',         'TEXT',    NULL,  NULL, FALSE, FALSE, TRUE,  2),
    ('cpu', 'cores',               'Bërthamat',           'Cores',              'INTEGER', NULL,  NULL, FALSE, TRUE,  TRUE,  3),
    ('cpu', 'threads',             'Thread-et',           'Threads',            'INTEGER', NULL,  NULL, FALSE, FALSE, FALSE, 4),
    ('cpu', 'base_clock_ghz',      'Frekuenca bazë',      'Base clock',         'DECIMAL', 'GHz', NULL, FALSE, FALSE, FALSE, 5),
    ('cpu', 'boost_clock_ghz',     'Frekuenca boost',     'Boost clock',        'DECIMAL', 'GHz', NULL, FALSE, FALSE, FALSE, 6),
    ('cpu', 'tdp_watts',           'Konsumi (TDP)',       'Power (TDP)',        'INTEGER', 'W',   NULL, FALSE, FALSE, FALSE, 7),
    ('cpu', 'integrated_graphics', 'Grafikë e integruar', 'Integrated graphics','BOOLEAN', NULL,  NULL, FALSE, FALSE, TRUE,  8),
    ('cpu', 'cooler_included',     'Me ftohës',           'Cooler included',    'BOOLEAN', NULL,  NULL, FALSE, FALSE, FALSE, 9),

    ('ram', 'type',          'Tipi',             'Type',          'ENUM',    NULL,  '["DDR3","DDR4","DDR5"]', FALSE,  TRUE,  TRUE,  1),
    ('ram', 'capacity_gb',   'Kapaciteti',       'Capacity',      'INTEGER', 'GB',  NULL,                     FALSE,  TRUE,  TRUE,  2),
    ('ram', 'kit_config',    'Konfigurimi',      'Kit',           'TEXT',    NULL,  NULL,                     FALSE, FALSE, FALSE, 3),
    ('ram', 'speed_mhz',     'Shpejtësia',       'Speed',         'INTEGER', 'MHz', NULL,                     FALSE, TRUE,  TRUE,  4),
    ('ram', 'cas_latency',   'Latenca (CL)',     'CAS latency',   'INTEGER', NULL,  NULL,                     FALSE, FALSE, FALSE, 5),
    ('ram', 'rgb',           'RGB',              'RGB',           'BOOLEAN', NULL,  NULL,                     FALSE, FALSE, FALSE, 6),
    ('ram', 'heatspreader',  'Me heatspreader',  'Heatspreader',  'BOOLEAN', NULL,  NULL,                     FALSE, FALSE, FALSE, 7),

    ('storage', 'type',            'Tipi',                 'Type',           'ENUM',    NULL,   '["NVMe","SATA SSD","HDD"]', FALSE,  TRUE,  TRUE,  1),
    ('storage', 'capacity_gb',     'Kapaciteti',           'Capacity',       'INTEGER', 'GB',   NULL,                        FALSE,  TRUE,  TRUE,  2),
    ('storage', 'form_factor',     'Formati',              'Form factor',    'TEXT',    NULL,   NULL,                        FALSE, FALSE, FALSE, 3),
    ('storage', 'interface',       'Ndërfaqja',            'Interface',      'TEXT',    NULL,   NULL,                        FALSE, FALSE, FALSE, 4),
    ('storage', 'read_mbs',        'Leximi',               'Read speed',     'INTEGER', 'MB/s', NULL,                        FALSE, FALSE, FALSE, 5),
    ('storage', 'write_mbs',       'Shkrimi',              'Write speed',    'INTEGER', 'MB/s', NULL,                        FALSE, FALSE, FALSE, 6),
    ('storage', 'tbw_rating',      'Qëndrueshmëria (TBW)', 'Endurance (TBW)','INTEGER', 'TB',   NULL,                        FALSE, FALSE, FALSE, 7),
    ('storage', 'power_on_hours',  'Orë pune',             'Power-on hours', 'INTEGER', 'orë',  NULL,                        FALSE, FALSE, FALSE, 8),

    ('psu', 'wattage',            'Fuqia',                'Wattage',          'INTEGER', 'W',    NULL, FALSE,  TRUE,  TRUE,  1),
    ('psu', 'efficiency_rating',  'Efiçenca',             'Efficiency',       'ENUM',    NULL,   '["Pa certifikim","80+","80+ Bronze","80+ Silver","80+ Gold","80+ Platinum","80+ Titanium"]', FALSE, TRUE, TRUE, 2),
    ('psu', 'modularity',         'Modulariteti',         'Modularity',       'ENUM',    NULL,   '["Jo modular","Gjysmë modular","Plotësisht modular"]', FALSE, FALSE, TRUE, 3),
    ('psu', 'form_factor',        'Formati',              'Form factor',      'TEXT',    NULL,   NULL, FALSE, FALSE, FALSE, 4),
    ('psu', 'pcie_connectors',    'Konektorët PCIe',      'PCIe connectors',  'TEXT',    NULL,   NULL, FALSE, FALSE, FALSE, 5),
    ('psu', 'warranty_years',     'Garancia e prodhuesit','Maker warranty',   'INTEGER', 'vite', NULL, FALSE, FALSE, FALSE, 6),

    ('motherboard', 'socket',       'Soketi',            'Socket',        'TEXT',    NULL, NULL,                                     FALSE,  TRUE,  TRUE,  1),
    ('motherboard', 'chipset',      'Çipseti',           'Chipset',       'TEXT',    NULL, NULL,                                     FALSE, TRUE,  TRUE,  2),
    ('motherboard', 'form_factor',  'Formati',           'Form factor',   'ENUM',    NULL, '["ATX","Micro-ATX","Mini-ITX","E-ATX"]', FALSE, FALSE, TRUE,  3),
    ('motherboard', 'ram_slots',    'Slote RAM',         'RAM slots',     'INTEGER', NULL, NULL,                                     FALSE, FALSE, FALSE, 4),
    ('motherboard', 'max_ram_gb',   'RAM maksimale',     'Max RAM',       'INTEGER', 'GB', NULL,                                     FALSE, FALSE, FALSE, 5),
    ('motherboard', 'm2_slots',     'Slote M.2',         'M.2 slots',     'INTEGER', NULL, NULL,                                     FALSE, FALSE, FALSE, 6),
    ('motherboard', 'sata_ports',   'Porta SATA',        'SATA ports',    'INTEGER', NULL, NULL,                                     FALSE, FALSE, FALSE, 7),
    ('motherboard', 'wifi',         'Wi-Fi',             'Wi-Fi',         'BOOLEAN', NULL, NULL,                                     FALSE, FALSE, TRUE,  8),

    ('case', 'form_factor',           'Formati',                  'Form factor',        'ENUM',    NULL, '["Full Tower","Mid Tower","Micro-ATX","Mini-ITX"]', FALSE, TRUE, TRUE, 1),
    ('case', 'max_gpu_length_mm',     'Gjatësia maks. e GPU',     'Max GPU length',     'INTEGER', 'mm', NULL, FALSE, FALSE, FALSE, 2),
    ('case', 'max_cooler_height_mm',  'Lartësia maks. e ftohësit','Max cooler height',  'INTEGER', 'mm', NULL, FALSE, FALSE, FALSE, 3),
    ('case', 'included_fans',         'Ventilatorë të përfshirë', 'Included fans',      'INTEGER', NULL, NULL, FALSE, FALSE, FALSE, 4),
    ('case', 'side_panel',            'Paneli anësor',            'Side panel',         'ENUM',    NULL, '["Xham","Rrjetë","Metal","Akrilik"]', FALSE, FALSE, FALSE, 5),
    ('case', 'front_panel_io',        'Portat përpara',           'Front panel I/O',    'TEXT',    NULL, NULL, FALSE, FALSE, FALSE, 6),

    ('cooling', 'type',          'Tipi',        'Type',     'ENUM',    NULL, '["Ajër","Ujë (AIO)"]', FALSE,  TRUE,  TRUE,  1),
    ('cooling', 'sockets',       'Soketet',     'Sockets',  'TEXT',    NULL, NULL,                   FALSE, FALSE, FALSE, 2),
    ('cooling', 'height_mm',     'Lartësia',    'Height',   'INTEGER', 'mm', NULL,                   FALSE, FALSE, FALSE, 3),
    ('cooling', 'radiator_mm',   'Radiatori',   'Radiator', 'INTEGER', 'mm', NULL,                   FALSE, TRUE,  TRUE,  4),
    ('cooling', 'rgb',           'RGB',         'RGB',      'BOOLEAN', NULL, NULL,                   FALSE, FALSE, FALSE, 5),

    ('monitor', 'size_inch',     'Madhësia',          'Size',          'DECIMAL', 'inç', NULL,                                  FALSE,  TRUE,  TRUE,  1),
    ('monitor', 'resolution',    'Rezolucioni',       'Resolution',    'ENUM',    NULL,  '["1920x1080","2560x1080","2560x1440","3440x1440","3840x2160"]', FALSE, TRUE, TRUE, 2),
    ('monitor', 'refresh_hz',    'Frekuenca',         'Refresh rate',  'INTEGER', 'Hz',  NULL,                                  FALSE, TRUE,  TRUE,  3),
    ('monitor', 'panel_type',    'Paneli',            'Panel',         'ENUM',    NULL,  '["IPS","VA","TN","OLED"]',            FALSE, FALSE, TRUE,  4),
    ('monitor', 'response_ms',   'Koha e përgjigjes', 'Response time', 'DECIMAL', 'ms',  NULL,                                  FALSE, FALSE, FALSE, 5),
    ('monitor', 'inputs',        'Hyrjet',            'Inputs',        'TEXT',    NULL,  NULL,                                  FALSE, FALSE, FALSE, 6);

INSERT INTO category_field_template (category_id, field_key, label_sq, label_en, data_type, unit, enum_options, is_required, show_in_listing, show_in_filters, sort_order)
SELECT c.id, t.k, t.sq, t.en, t.dt, t.unit, t.opts, t.req, t.list, t.filt, t.o
FROM category c JOIN seed_field_template t ON t.catalog_slug = c.catalog_slug;

DROP TEMPORARY TABLE seed_field_template;

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'vram_gb', s.spec_value = REGEXP_REPLACE(s.spec_value, '^\\s*([0-9]+)\\s*GB\\s*$', '$1')
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'VRAM' AND s.spec_value REGEXP '^\\s*[0-9]+\\s*GB\\s*$';

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'memory_type'
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'Tipi i memories';

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'tdp_watts', s.spec_value = REGEXP_REPLACE(s.spec_value, '^\\s*([0-9]+)\\s*W\\s*$', '$1')
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'TDP' AND s.spec_value REGEXP '^\\s*[0-9]+\\s*W\\s*$';

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'psu_min_watts', s.spec_value = REGEXP_REPLACE(s.spec_value, '^\\s*([0-9]+)\\s*W\\s*$', '$1')
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'PSU minimale' AND s.spec_value REGEXP '^\\s*[0-9]+\\s*W\\s*$';

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'pcie_connectors'
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'Konektorët';

UPDATE product_spec s JOIN product p ON s.product_id = p.id
SET s.spec_key = 'length_mm', s.spec_value = REGEXP_REPLACE(s.spec_value, '^\\s*([0-9]+)\\s*mm\\s*$', '$1')
WHERE p.category_slug = 'karta-grafike' AND s.spec_key = 'Gjatësia' AND s.spec_value REGEXP '^\\s*[0-9]+\\s*mm\\s*$';
