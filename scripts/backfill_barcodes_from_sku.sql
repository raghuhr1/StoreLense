-- Backfill products.barcodes for products where the EAN was stored directly in sku
-- (sku = 13-digit EAN, products.barcodes has zero rows for these products).
--
-- Background: EanResolutionService/existsByEan checks products.barcodes only, never sku.
-- Products imported with sku=EAN therefore show UNRESOLVED on ERP import even though
-- they exist. This backfill inserts the missing barcode row so EAN-based lookups
-- (existsByEan, getEpcsByEan, by-ean/{ean}/exists) start finding them.
--
-- Run this manually and review the SELECT preview before running the INSERT.

-- 1) Preview: which products would be affected
SELECT p.id AS product_id, p.sku, p.name
FROM products.products p
WHERE p.sku ~ '^[0-9]{12,14}$'                -- sku looks like an EAN (12-14 digits)
  AND NOT EXISTS (
      SELECT 1 FROM products.barcodes b WHERE b.product_id = p.id
  )
ORDER BY p.sku;

-- 2) Backfill: insert one primary EAN barcode row per affected product
-- Uncomment to run after reviewing the preview above.

-- INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
-- SELECT p.id, 'ean13', p.sku, true
-- FROM products.products p
-- WHERE p.sku ~ '^[0-9]{12,14}$'
--   AND NOT EXISTS (
--       SELECT 1 FROM products.barcodes b WHERE b.product_id = p.id
--   )
-- ON CONFLICT (barcode_value) DO NOTHING;

-- 3) Verify after running
-- SELECT p.sku, count(b.id) AS barcode_rows
-- FROM products.products p
-- LEFT JOIN products.barcodes b ON b.product_id = p.id
-- WHERE p.sku ~ '^[0-9]{12,14}$'
-- GROUP BY p.sku
-- HAVING count(b.id) = 0;
