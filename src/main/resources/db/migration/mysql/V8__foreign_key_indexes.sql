-- Kept in step with postgresql/V8. MySQL already indexes foreign key columns on its own, so only
-- the indexes that also cover the sort order (and the order date) add anything here.
CREATE INDEX ix_image_product  ON product_image (product_id, sort_order);
CREATE INDEX ix_spec_product   ON product_spec (product_id, sort_order);
CREATE INDEX ix_orders_created ON orders (created_at);
