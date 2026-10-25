-- Optional barcode (EAN/UPC/GTIN) from the product's box, published as the Product's gtin.
ALTER TABLE product ADD COLUMN gtin VARCHAR(14);
