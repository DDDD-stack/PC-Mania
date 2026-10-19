-- The first non-GPU stock (October 2026): a Ryzen 7 5700X, two Deepcool coolers and two A520 boards.
-- 1. Motherboards get a RAM type field, so the assistant can tell which memory fits.
-- 2. The autofill catalogue gets a verified row for each model, checked against the makers' sheets.
-- 3. The five products get a description and their template specs, but only where the admin has not
--    filled them in already: a product that has any spec, or a description, keeps what it has.
-- On a database without these products (tests, a fresh install) step 3 does nothing.

UPDATE category_field_template t SET sort_order = t.sort_order + 1
FROM category c WHERE t.category_id = c.id AND c.catalog_slug = 'motherboard' AND t.sort_order >= 3
  AND NOT EXISTS (SELECT 1 FROM category_field_template o WHERE o.category_id = c.id AND o.field_key = 'ram_type');

INSERT INTO category_field_template (category_id, field_key, label_sq, label_en, data_type, unit, enum_options, is_required, show_in_listing, show_in_filters, sort_order)
SELECT c.id, 'ram_type', 'Tipi i RAM-it', 'RAM type', 'ENUM', NULL, '["DDR3","DDR4","DDR5"]', FALSE, FALSE, TRUE, 3
FROM category c WHERE c.catalog_slug = 'motherboard'
  AND NOT EXISTS (SELECT 1 FROM category_field_template o WHERE o.category_id = c.id AND o.field_key = 'ram_type');

INSERT INTO spec_catalog_entry (category_slug, name, aliases, spec_json, verified)
SELECT v.cat, v.name, v.aliases, v.spec::jsonb, TRUE
FROM (VALUES
    ('cpu', 'AMD Ryzen 7 5700X', '5700X, Ryzen 7 5700X, R7 5700X',
     '{"socket":"AM4","generation":"Ryzen 5000 (Zen 3)","cores":"8","threads":"16","base_clock_ghz":"3.4","boost_clock_ghz":"4.6","tdp_watts":"65","integrated_graphics":"false","cooler_included":"false"}'),
    ('cooling', 'Deepcool AK400 Digital SE', 'AK400 Digital SE, AK400 Digital, AK400',
     '{"type":"Ajër","sockets":"Intel LGA1851/1700/1200/115x, AMD AM5/AM4","height_mm":"157","rgb":"true"}'),
    ('cooling', 'Deepcool LE360 V2', 'LE360 V2, LE360, Deepcool LE360',
     '{"type":"Ujë (AIO)","sockets":"Intel LGA1851/1700/1200/115x, AMD AM5/AM4","radiator_mm":"360","rgb":"true"}'),
    ('motherboard', 'Gigabyte A520M K V2', 'A520M K V2, A520M K, Gigabyte A520M',
     '{"socket":"AM4","chipset":"A520","ram_type":"DDR4","form_factor":"Micro-ATX","ram_slots":"2","max_ram_gb":"64","m2_slots":"1","sata_ports":"4","wifi":"false"}'),
    ('motherboard', 'MSI A520M-A Pro', 'A520M-A Pro, A520M Pro, MSI A520M, PRO A520M-S',
     '{"socket":"AM4","chipset":"A520","ram_type":"DDR4","form_factor":"Micro-ATX","ram_slots":"2","max_ram_gb":"64","m2_slots":"1","sata_ports":"4","wifi":"false"}')
) AS v(cat, name, aliases, spec)
WHERE NOT EXISTS (SELECT 1 FROM spec_catalog_entry e WHERE e.category_slug = v.cat AND lower(e.name) = lower(v.name));

UPDATE product p SET
    model = COALESCE(NULLIF(trim(p.model), ''), v.model),
    short_description = COALESCE(NULLIF(trim(p.short_description), ''), v.short_d),
    full_description = COALESCE(NULLIF(trim(p.full_description), ''), v.full_d)
FROM (VALUES
    ('amd-ryzen-7-5700x', 'Ryzen 7 5700X',
     'Procesor me 8 bërthama dhe 16 thread-e për soketin AM4, deri në 4,6 GHz me vetëm 65 W. Nuk vjen me ftohës.',
     'Ryzen 7 5700X është një nga procesorët me vlerën më të mirë për platformën AM4: 8 bërthama dhe 16 thread-e Zen 3, frekuencë deri në 4,6 GHz dhe vetëm 65 W konsum. Shkon shumë mirë për gaming në 1080p dhe 1440p, për streaming dhe për punë si montimi i videove. Kini parasysh: nuk vjen me ftohës në kuti dhe nuk ka grafikë të integruar, prandaj i duhen një ftohës dhe një kartë grafike. Punon me pllaka amë AM4 (A520, B450, B550, X570); në pllakat më të vjetra mund të duhet përditësim i BIOS-it.'),
    ('ftohes-ak400-digital-se', 'AK400 Digital SE',
     'Ftohës ajri me ventilator 120 mm dhe ekran dixhital që tregon temperaturën e procesorit. Për Intel dhe AMD AM4/AM5.',
     'Deepcool AK400 Digital SE është ftohës ajri me 4 tuba nxehtësie dhe ventilator 120 mm PWM (500–1850 RPM, deri në 28 dB), i qetë edhe nën ngarkesë. Ekrani dixhital sipër tregon temperaturën dhe ngarkesën e procesorit në kohë reale, dhe ndriçimi ARGB lidhet me pllakën amë. Mjafton për procesorë si Ryzen 7 5700X ose Core i5/i7 pa overclock. Përshtatet me Intel LGA1851/1700/1200/115x dhe AMD AM5/AM4. Lartësia është 157 mm, prandaj kontrolloni që kasa juaj e pranon. Ekrani kërkon një portë USB 2.0 të lirë në pllakën amë.'),
    ('ftohes-liquid-deepcool-le360-v2', 'LE360 V2',
     'Ftohje me ujë (AIO) me radiator 360 mm dhe 3 ventilatorë ARGB 120 mm. Për Intel dhe AMD AM4/AM5.',
     'Deepcool LE360 V2 është ftohje me ujë all-in-one me radiator 360 mm dhe tre ventilatorë 120 mm ARGB (500–2100 RPM), për procesorë që nxehen shumë ose për kë do një PC të qetë dhe të pastër nga brenda. Pompa ka mbrojtje kundër rrjedhjeve dhe ndriçim ARGB, ashtu si ventilatorët. Përshtatet me Intel LGA1851/1700/1200/115x dhe AMD AM5/AM4. Radiatori është 402 × 120 × 27 mm, prandaj kontrolloni që kasa juaj ka vend për radiator 360 mm, zakonisht sipër ose përpara.'),
    ('motherboard-gigabyte-a520m-am4', 'A520M K V2',
     'Pllakë amë Micro-ATX për AM4 me çipset A520: 2 slote DDR4, 1 slot M.2 për SSD NVMe. Gati për Ryzen 5000.',
     'Gigabyte A520M K V2 është pllakë amë Micro-ATX për soketin AM4, zgjidhje e thjeshtë dhe e besueshme për një PC gaming ose zyre me buxhet. Ka 2 slote DDR4 (deri në 64 GB), 1 slot M.2 për SSD NVMe PCIe 3.0, 4 porta SATA, LAN gigabit, HDMI dhe VGA. Mbështet procesorët Ryzen 5000 (p.sh. Ryzen 5 5600 ose Ryzen 7 5700X) dhe shumicën e Ryzen 3000; nuk mbështet Ryzen 1000 dhe 2000. Nuk ka Wi-Fi.'),
    ('motherboard-msi-a520m-tomawhok', 'A520M-A Pro',
     'Pllakë amë Micro-ATX për AM4 me çipset A520: 2 slote DDR4, 1 slot M.2 për SSD NVMe. Për Ryzen 3000 dhe 5000.',
     'MSI A520M-A Pro është pllakë amë Micro-ATX për soketin AM4, e përshtatshme për një PC gaming ose zyre me buxhet. Ka 2 slote DDR4 (deri në 64 GB), 1 slot M.2 për SSD NVMe ose SATA, 4 porta SATA dhe LAN gigabit. Mbështet procesorët Ryzen 5000 (p.sh. Ryzen 5 5600 ose Ryzen 7 5700X) dhe shumicën e Ryzen 3000; nuk mbështet Ryzen 1000 dhe 2000. Nuk ka Wi-Fi.')
) AS v(slug, model, short_d, full_d)
WHERE p.slug = v.slug;

INSERT INTO product_spec (product_id, spec_key, spec_value, sort_order)
SELECT p.id, v.k, v.val, v.o
FROM product p JOIN (VALUES
    ('amd-ryzen-7-5700x', 'socket', 'AM4', 1),
    ('amd-ryzen-7-5700x', 'generation', 'Ryzen 5000 (Zen 3)', 2),
    ('amd-ryzen-7-5700x', 'cores', '8', 3),
    ('amd-ryzen-7-5700x', 'threads', '16', 4),
    ('amd-ryzen-7-5700x', 'base_clock_ghz', '3.4', 5),
    ('amd-ryzen-7-5700x', 'boost_clock_ghz', '4.6', 6),
    ('amd-ryzen-7-5700x', 'tdp_watts', '65', 7),
    ('amd-ryzen-7-5700x', 'integrated_graphics', 'false', 8),
    ('amd-ryzen-7-5700x', 'cooler_included', 'false', 9),
    ('ftohes-ak400-digital-se', 'type', 'Ajër', 1),
    ('ftohes-ak400-digital-se', 'sockets', 'Intel LGA1851/1700/1200/115x, AMD AM5/AM4', 2),
    ('ftohes-ak400-digital-se', 'height_mm', '157', 3),
    ('ftohes-ak400-digital-se', 'rgb', 'true', 5),
    ('ftohes-liquid-deepcool-le360-v2', 'type', 'Ujë (AIO)', 1),
    ('ftohes-liquid-deepcool-le360-v2', 'sockets', 'Intel LGA1851/1700/1200/115x, AMD AM5/AM4', 2),
    ('ftohes-liquid-deepcool-le360-v2', 'radiator_mm', '360', 4),
    ('ftohes-liquid-deepcool-le360-v2', 'rgb', 'true', 5),
    ('motherboard-gigabyte-a520m-am4', 'socket', 'AM4', 1),
    ('motherboard-gigabyte-a520m-am4', 'chipset', 'A520', 2),
    ('motherboard-gigabyte-a520m-am4', 'ram_type', 'DDR4', 3),
    ('motherboard-gigabyte-a520m-am4', 'form_factor', 'Micro-ATX', 4),
    ('motherboard-gigabyte-a520m-am4', 'ram_slots', '2', 5),
    ('motherboard-gigabyte-a520m-am4', 'max_ram_gb', '64', 6),
    ('motherboard-gigabyte-a520m-am4', 'm2_slots', '1', 7),
    ('motherboard-gigabyte-a520m-am4', 'sata_ports', '4', 8),
    ('motherboard-gigabyte-a520m-am4', 'wifi', 'false', 9),
    ('motherboard-msi-a520m-tomawhok', 'socket', 'AM4', 1),
    ('motherboard-msi-a520m-tomawhok', 'chipset', 'A520', 2),
    ('motherboard-msi-a520m-tomawhok', 'ram_type', 'DDR4', 3),
    ('motherboard-msi-a520m-tomawhok', 'form_factor', 'Micro-ATX', 4),
    ('motherboard-msi-a520m-tomawhok', 'ram_slots', '2', 5),
    ('motherboard-msi-a520m-tomawhok', 'max_ram_gb', '64', 6),
    ('motherboard-msi-a520m-tomawhok', 'm2_slots', '1', 7),
    ('motherboard-msi-a520m-tomawhok', 'sata_ports', '4', 8),
    ('motherboard-msi-a520m-tomawhok', 'wifi', 'false', 9)
) AS v(slug, k, val, o) ON p.slug = v.slug
WHERE NOT EXISTS (SELECT 1 FROM product_spec s WHERE s.product_id = p.id);
