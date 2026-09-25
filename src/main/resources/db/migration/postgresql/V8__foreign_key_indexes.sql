-- Postgres, unlike MySQL, does not index the referencing side of a foreign key. Without these,
-- every product card, product page and order lookup reads the whole child table to find a
-- product's photos, specs or an order's lines - fine with a handful of rows, and slower with
-- every listing added. The sort column is included where the query also orders by it.
CREATE INDEX IF NOT EXISTS ix_image_product    ON product_image (product_id, sort_order);
CREATE INDEX IF NOT EXISTS ix_spec_product     ON product_spec (product_id, sort_order);
CREATE INDEX IF NOT EXISTS ix_item_order       ON order_item (order_id);
CREATE INDEX IF NOT EXISTS ix_item_product     ON order_item (product_id);
CREATE INDEX IF NOT EXISTS ix_token_admin      ON api_token (admin_user_id);
CREATE INDEX IF NOT EXISTS ix_upcoming_product ON upcoming_product (product_id);
-- The admin order list without a status filter sorts every order by date.
CREATE INDEX IF NOT EXISTS ix_orders_created   ON orders (created_at);
