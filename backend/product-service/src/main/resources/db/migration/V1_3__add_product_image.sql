ALTER TABLE products.products
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
