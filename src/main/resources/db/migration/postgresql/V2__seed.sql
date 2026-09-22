INSERT INTO category (name_sq, slug, sort_order, icon_class) VALUES
    ('Karta grafike', 'karta-grafike', 1, 'bi-gpu-card'),
    ('Procesorë', 'procesore', 2, 'bi-cpu'),
    ('Pllaka amë', 'pllaka-ame', 3, 'bi-motherboard'),
    ('Memorie RAM', 'memorie-ram', 4, 'bi-memory'),
    ('Disqe SSD / HDD', 'disqe', 5, 'bi-device-ssd'),
    ('Furnizues energjie', 'furnizues-energjie', 6, 'bi-plug'),
    ('Kasa', 'kasa', 7, 'bi-pc'),
    ('Ftohje', 'ftohje', 8, 'bi-fan'),
    ('Monitorë', 'monitore', 9, 'bi-display');

INSERT INTO brand (name, slug) VALUES
    ('ASUS', 'asus'), ('MSI', 'msi'), ('Gigabyte', 'gigabyte'), ('Sapphire', 'sapphire'),
    ('Zotac', 'zotac'), ('PowerColor', 'powercolor'), ('AMD', 'amd'), ('Intel', 'intel'),
    ('Corsair', 'corsair'), ('Kingston', 'kingston'), ('Samsung', 'samsung');

INSERT INTO product (title, brand_id, model, category_slug, slug, item_condition, price_lek, cost_lek, quantity, status,
                     short_description, full_description, warranty_days, test_notes, is_mining_free, transport_included,
                     created_at, listed_at, view_count)
VALUES
    ('MSI GeForce RTX 3060 Ventus 2X 12G OC', (SELECT id FROM brand WHERE slug = 'msi'), 'RTX 3060 Ventus 2X 12G OC',
     'karta-grafike', 'msi-geforce-rtx-3060-ventus-2x-12g-oc', 'USED', 27000, 20500, 1, 'ACTIVE',
     'RTX 3060 me 12 GB VRAM, e testuar, pa minim. Ideale për lojëra në 1080p.',
     '<p>Kartë grafike e përdorur nga një lojtar, në gjendje shumë të mirë. Pastruar dhe me pastë termike të re.</p><ul><li>Kutia origjinale</li><li>Pa probleme me coil whine</li></ul>',
     30, 'Test 30 min FurMark: max 71°C GPU, 84°C hotspot. Cyberpunk 2077 1080p High: ~70 FPS.', TRUE, FALSE,
     NOW() - INTERVAL '41 days', NOW() - INTERVAL '40 days', 0),
    ('Gigabyte GeForce RTX 4070 Super Windforce OC 12G', (SELECT id FROM brand WHERE slug = 'gigabyte'), 'RTX 4070 Super Windforce OC',
     'karta-grafike', 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'NEW', 72000, 62000, 2, 'ACTIVE',
     'E re, e mbyllur në kuti, me garanci 2 vjet. Performancë e shkëlqyer në 1440p.',
     '<p>Kartë grafike e re me DLSS 3.5 dhe ray tracing. Konsum i ulët energjie për performancën që ofron.</p>',
     730, NULL, TRUE, TRUE,
     NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', 0),
    ('ASUS TUF Gaming GeForce RTX 4080 Super OC 16G', (SELECT id FROM brand WHERE slug = 'asus'), 'TUF RTX 4080 Super OC',
     'karta-grafike', 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'OPEN_BOX', 125000, 108000, 1, 'ACTIVE',
     'Open box – e hapur vetëm për test. Të gjithë aksesorët e përfshirë.',
     '<p>Kutia është hapur vetëm për fotografim dhe test. Kartela nuk është përdorur për lojëra apo minim.</p>',
     365, 'Test 15 min 3DMark Time Spy: max 63°C. Time Spy score: 24 100.', TRUE, TRUE,
     NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days', 0);

INSERT INTO product_spec (product_id, spec_key, spec_value, sort_order)
SELECT p.id, s.k, s.v, s.o FROM product p JOIN (
    SELECT 'msi-geforce-rtx-3060-ventus-2x-12g-oc' AS slug, 'VRAM' AS k, '12 GB' AS v, 1 AS o UNION ALL
    SELECT 'msi-geforce-rtx-3060-ventus-2x-12g-oc', 'Tipi i memories', 'GDDR6', 2 UNION ALL
    SELECT 'msi-geforce-rtx-3060-ventus-2x-12g-oc', 'Bus', 'PCIe 4.0 x16', 3 UNION ALL
    SELECT 'msi-geforce-rtx-3060-ventus-2x-12g-oc', 'TDP', '170 W', 4 UNION ALL
    SELECT 'msi-geforce-rtx-3060-ventus-2x-12g-oc', 'Gjatësia', '235 mm', 5 UNION ALL
    SELECT 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'VRAM', '12 GB', 1 UNION ALL
    SELECT 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'Tipi i memories', 'GDDR6X', 2 UNION ALL
    SELECT 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'Bus', 'PCIe 4.0 x16', 3 UNION ALL
    SELECT 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'TDP', '220 W', 4 UNION ALL
    SELECT 'gigabyte-geforce-rtx-4070-super-windforce-oc-12g', 'Gjatësia', '261 mm', 5 UNION ALL
    SELECT 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'VRAM', '16 GB', 1 UNION ALL
    SELECT 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'Tipi i memories', 'GDDR6X', 2 UNION ALL
    SELECT 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'Bus', 'PCIe 4.0 x16', 3 UNION ALL
    SELECT 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'TDP', '320 W', 4 UNION ALL
    SELECT 'asus-tuf-gaming-geforce-rtx-4080-super-oc-16g', 'Gjatësia', '348 mm', 5
) s ON s.slug = p.slug;
