-- Per-category display toggles: hide a category entirely, or keep it listed but flagged as out of stock.
ALTER TABLE category
    ADD COLUMN is_visible      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN is_out_of_stock BOOLEAN NOT NULL DEFAULT FALSE;

-- Only GPUs are stocked today: hide the rest until there is something to sell.
UPDATE category SET is_visible = FALSE WHERE slug <> 'karta-grafike';
