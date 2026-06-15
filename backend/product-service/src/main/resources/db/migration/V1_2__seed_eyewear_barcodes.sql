-- =============================================================================
-- StoreLense: Seed EAN-13 barcodes for eyewear / SOH demo products.
-- Maps known EANs (from ERP test CSV) to product SKUs.
-- Idempotent: ON CONFLICT DO NOTHING.
-- =============================================================================

SET search_path TO products;

INSERT INTO barcodes (id, product_id, barcode_type, barcode_value, is_primary)
SELECT
    gen_random_uuid(),
    p.id,
    'ean13',
    b.barcode_value,
    true
FROM (VALUES
    ('Sunglass0001', '8901230001008'),
    ('Sunglass0002', '8901230000995'),
    ('Sunglass0003', '8901230000988'),
    ('Sunglass0004', '8901230000971')
) AS b(sku, barcode_value)
JOIN products p ON p.sku = b.sku
ON CONFLICT (barcode_value) DO NOTHING;
