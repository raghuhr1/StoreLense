-- =============================================================================
-- StoreLense: Seed EAN-13 barcodes for all RFID-enabled demo products.
-- These EANs are what POS prints on receipts; the C66 gate app scans
-- the bill QR (which contains EANs) and resolves them to EPCs via this table.
--
-- Idempotent: ON CONFLICT DO NOTHING on the unique barcode_value constraint.
-- =============================================================================

INSERT INTO products.barcodes (id, product_id, barcode_type, barcode_value, is_primary)
SELECT
    gen_random_uuid(),
    p.id,
    'ean13',
    b.barcode_value,
    true
FROM (VALUES
    ('APP-DNM-BLU-001', '8901234567890'),
    ('APP-DNM-BLK-001', '8901234567891'),
    ('APP-TEE-WHT-001', '8901234567892'),
    ('APP-TEE-BLK-001', '8901234567893'),
    ('APP-POL-NAV-001', '8901234567894'),
    ('APP-HOD-GRY-001', '8901234567895'),
    ('APP-JKT-KHA-001', '8901234567896'),
    ('FTW-SNK-WHT-001', '8901234567897'),
    ('FTW-RNR-BLK-001', '8901234567898'),
    ('FTW-SND-TAN-001', '8901234567899'),
    ('ACC-CAP-BLK-001', '8901234567900'),
    ('APP-SCK-PKG-001', '8901234567901'),
    ('APP-SHT-STR-001', '8901234567902')
) AS b(sku, barcode_value)
JOIN products.products p ON p.sku = b.sku
ON CONFLICT (barcode_value) DO NOTHING;
