-- StoreLense: Bulk product import from T2_products.csv
-- Run: psql -h localhost -U storelense_app -d storelense -f /tmp/import_products.sql

BEGIN;

-- Step 1: Ensure categories exist
INSERT INTO products.product_categories (code, name) VALUES
  ('APPAREL_BOTTOMS',   'Apparel Bottoms'),
  ('APPAREL_TOPS',      'Apparel Tops'),
  ('APPAREL_OUTERWEAR', 'Apparel Outerwear'),
  ('APPAREL_UNDERWEAR', 'Apparel Underwear'),
  ('FOOTWEAR_CASUAL',   'Footwear Casual'),
  ('FOOTWEAR_FORMAL',   'Footwear Formal'),
  ('ACCESSORIES',       'Accessories')
ON CONFLICT (code) DO NOTHING;

-- Step 2: Insert products + barcodes
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLU-28', 'Denim Jeans Blue - 28W', 'Slim Fit Mid-Rise Blue Denim Jeans Waist 28', 'Pantaloons', 'ERP-PANT-001', 'EACH', 650, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560001', true FROM products.products WHERE sku='PANT-DEN-BLU-28'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLU-30', 'Denim Jeans Blue - 30W', 'Slim Fit Mid-Rise Blue Denim Jeans Waist 30', 'Pantaloons', 'ERP-PANT-002', 'EACH', 670, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560002', true FROM products.products WHERE sku='PANT-DEN-BLU-30'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLU-32', 'Denim Jeans Blue - 32W', 'Slim Fit Mid-Rise Blue Denim Jeans Waist 32', 'Pantaloons', 'ERP-PANT-003', 'EACH', 690, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560003', true FROM products.products WHERE sku='PANT-DEN-BLU-32'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLU-34', 'Denim Jeans Blue - 34W', 'Slim Fit Mid-Rise Blue Denim Jeans Waist 34', 'Pantaloons', 'ERP-PANT-004', 'EACH', 710, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560004', true FROM products.products WHERE sku='PANT-DEN-BLU-34'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLK-30', 'Denim Jeans Black - 30W', 'Slim Fit Mid-Rise Black Denim Jeans Waist 30', 'Pantaloons', 'ERP-PANT-005', 'EACH', 670, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560005', true FROM products.products WHERE sku='PANT-DEN-BLK-30'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLK-32', 'Denim Jeans Black - 32W', 'Slim Fit Mid-Rise Black Denim Jeans Waist 32', 'Pantaloons', 'ERP-PANT-006', 'EACH', 690, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560006', true FROM products.products WHERE sku='PANT-DEN-BLK-32'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('PANT-DEN-BLK-34', 'Denim Jeans Black - 34W', 'Slim Fit Mid-Rise Black Denim Jeans Waist 34', 'Pantaloons', 'ERP-PANT-007', 'EACH', 710, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560007', true FROM products.products WHERE sku='PANT-DEN-BLK-34'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TROP-CHI-KHA-30', 'Chinos Khaki - 30W', 'Regular Fit Chino Trouser Khaki 30W', 'Pantaloons', 'ERP-PANT-008', 'EACH', 520, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560008', true FROM products.products WHERE sku='TROP-CHI-KHA-30'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TROP-CHI-KHA-32', 'Chinos Khaki - 32W', 'Regular Fit Chino Trouser Khaki 32W', 'Pantaloons', 'ERP-PANT-009', 'EACH', 540, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560009', true FROM products.products WHERE sku='TROP-CHI-KHA-32'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TROP-CHI-NAV-30', 'Chinos Navy - 30W', 'Regular Fit Chino Trouser Navy 30W', 'Pantaloons', 'ERP-PANT-010', 'EACH', 520, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560010', true FROM products.products WHERE sku='TROP-CHI-NAV-30'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TROP-CHI-NAV-32', 'Chinos Navy - 32W', 'Regular Fit Chino Trouser Navy 32W', 'Pantaloons', 'ERP-PANT-011', 'EACH', 540, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560011', true FROM products.products WHERE sku='TROP-CHI-NAV-32'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-WHT-S', 'Formal Shirt White - S', 'Full Sleeve Formal Shirt White Small', 'Pantaloons', 'ERP-PANT-012', 'EACH', 230, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560012', true FROM products.products WHERE sku='SHRT-FRM-WHT-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-WHT-M', 'Formal Shirt White - M', 'Full Sleeve Formal Shirt White Medium', 'Pantaloons', 'ERP-PANT-013', 'EACH', 240, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560013', true FROM products.products WHERE sku='SHRT-FRM-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-WHT-L', 'Formal Shirt White - L', 'Full Sleeve Formal Shirt White Large', 'Pantaloons', 'ERP-PANT-014', 'EACH', 250, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560014', true FROM products.products WHERE sku='SHRT-FRM-WHT-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-WHT-XL', 'Formal Shirt White - XL', 'Full Sleeve Formal Shirt White XL', 'Pantaloons', 'ERP-PANT-015', 'EACH', 265, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560015', true FROM products.products WHERE sku='SHRT-FRM-WHT-XL'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-BLU-M', 'Formal Shirt Blue - M', 'Full Sleeve Formal Shirt Blue Medium', 'Pantaloons', 'ERP-PANT-016', 'EACH', 240, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560016', true FROM products.products WHERE sku='SHRT-FRM-BLU-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHRT-FRM-BLU-L', 'Formal Shirt Blue - L', 'Full Sleeve Formal Shirt Blue Large', 'Pantaloons', 'ERP-PANT-017', 'EACH', 250, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560017', true FROM products.products WHERE sku='SHRT-FRM-BLU-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-WHT-S', 'T-Shirt Crew White - S', 'Cotton Crew Neck T-Shirt White Small', 'Pantaloons', 'ERP-PANT-018', 'EACH', 180, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560018', true FROM products.products WHERE sku='TSHRT-CRW-WHT-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-WHT-M', 'T-Shirt Crew White - M', 'Cotton Crew Neck T-Shirt White Medium', 'Pantaloons', 'ERP-PANT-019', 'EACH', 190, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560019', true FROM products.products WHERE sku='TSHRT-CRW-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-WHT-L', 'T-Shirt Crew White - L', 'Cotton Crew Neck T-Shirt White Large', 'Pantaloons', 'ERP-PANT-020', 'EACH', 200, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560020', true FROM products.products WHERE sku='TSHRT-CRW-WHT-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-BLK-M', 'T-Shirt Crew Black - M', 'Cotton Crew Neck T-Shirt Black Medium', 'Pantaloons', 'ERP-PANT-021', 'EACH', 190, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560021', true FROM products.products WHERE sku='TSHRT-CRW-BLK-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-BLK-L', 'T-Shirt Crew Black - L', 'Cotton Crew Neck T-Shirt Black Large', 'Pantaloons', 'ERP-PANT-022', 'EACH', 200, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560022', true FROM products.products WHERE sku='TSHRT-CRW-BLK-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TSHRT-CRW-NVY-M', 'T-Shirt Crew Navy - M', 'Cotton Crew Neck T-Shirt Navy Medium', 'Pantaloons', 'ERP-PANT-023', 'EACH', 190, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560023', true FROM products.products WHERE sku='TSHRT-CRW-NVY-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('POLO-SLM-WHT-M', 'Polo Shirt White - M', 'Slim Fit Polo Shirt White Medium', 'Pantaloons', 'ERP-PANT-024', 'EACH', 270, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560024', true FROM products.products WHERE sku='POLO-SLM-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('POLO-SLM-WHT-L', 'Polo Shirt White - L', 'Slim Fit Polo Shirt White Large', 'Pantaloons', 'ERP-PANT-025', 'EACH', 280, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560025', true FROM products.products WHERE sku='POLO-SLM-WHT-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('POLO-SLM-BLU-M', 'Polo Shirt Blue - M', 'Slim Fit Polo Shirt Blue Medium', 'Pantaloons', 'ERP-PANT-026', 'EACH', 270, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560026', true FROM products.products WHERE sku='POLO-SLM-BLU-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('KURTA-CTN-WHT-M', 'Kurta Cotton White - M', 'Cotton Straight Kurta White Medium', 'Pantaloons', 'ERP-PANT-027', 'EACH', 310, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560027', true FROM products.products WHERE sku='KURTA-CTN-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('KURTA-CTN-WHT-L', 'Kurta Cotton White - L', 'Cotton Straight Kurta White Large', 'Pantaloons', 'ERP-PANT-028', 'EACH', 325, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560028', true FROM products.products WHERE sku='KURTA-CTN-WHT-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('KURTA-CTN-BEI-M', 'Kurta Cotton Beige - M', 'Cotton Straight Kurta Beige Medium', 'Pantaloons', 'ERP-PANT-029', 'EACH', 310, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560029', true FROM products.products WHERE sku='KURTA-CTN-BEI-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('KURTA-CTN-BEI-L', 'Kurta Cotton Beige - L', 'Cotton Straight Kurta Beige Large', 'Pantaloons', 'ERP-PANT-030', 'EACH', 325, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560030', true FROM products.products WHERE sku='KURTA-CTN-BEI-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('DRESS-FLR-BLU-S', 'Floral Dress Blue - S', 'Floral Print A-Line Dress Blue Small', 'Pantaloons', 'ERP-PANT-031', 'EACH', 380, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560031', true FROM products.products WHERE sku='DRESS-FLR-BLU-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('DRESS-FLR-BLU-M', 'Floral Dress Blue - M', 'Floral Print A-Line Dress Blue Medium', 'Pantaloons', 'ERP-PANT-032', 'EACH', 395, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560032', true FROM products.products WHERE sku='DRESS-FLR-BLU-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('DRESS-FLR-RED-S', 'Floral Dress Red - S', 'Floral Print A-Line Dress Red Small', 'Pantaloons', 'ERP-PANT-033', 'EACH', 380, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560033', true FROM products.products WHERE sku='DRESS-FLR-RED-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('DRESS-FLR-RED-M', 'Floral Dress Red - M', 'Floral Print A-Line Dress Red Medium', 'Pantaloons', 'ERP-PANT-034', 'EACH', 395, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560034', true FROM products.products WHERE sku='DRESS-FLR-RED-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TOP-CSS-PNK-S', 'Casual Top Pink - S', 'Casual Fit Round Neck Top Pink Small', 'Pantaloons', 'ERP-PANT-035', 'EACH', 165, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560035', true FROM products.products WHERE sku='TOP-CSS-PNK-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TOP-CSS-PNK-M', 'Casual Top Pink - M', 'Casual Fit Round Neck Top Pink Medium', 'Pantaloons', 'ERP-PANT-036', 'EACH', 175, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560036', true FROM products.products WHERE sku='TOP-CSS-PNK-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('TOP-CSS-WHT-M', 'Casual Top White - M', 'Casual Fit Round Neck Top White Medium', 'Pantaloons', 'ERP-PANT-037', 'EACH', 175, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_TOPS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560037', true FROM products.products WHERE sku='TOP-CSS-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('LEGG-STD-BLK-S', 'Leggings Black - S', 'Standard Fit Cotton Leggings Black Small', 'Pantaloons', 'ERP-PANT-038', 'EACH', 210, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560038', true FROM products.products WHERE sku='LEGG-STD-BLK-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('LEGG-STD-BLK-M', 'Leggings Black - M', 'Standard Fit Cotton Leggings Black Medium', 'Pantaloons', 'ERP-PANT-039', 'EACH', 220, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560039', true FROM products.products WHERE sku='LEGG-STD-BLK-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('LEGG-STD-NVY-M', 'Leggings Navy - M', 'Standard Fit Cotton Leggings Navy Medium', 'Pantaloons', 'ERP-PANT-040', 'EACH', 220, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_BOTTOMS'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560040', true FROM products.products WHERE sku='LEGG-STD-NVY-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-CAS-WHT-7', 'Casual Sneaker White - 7', 'Canvas Casual Sneaker White UK7', 'Pantaloons', 'ERP-PANT-041', 'PAIR', 480, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_CASUAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560041', true FROM products.products WHERE sku='SHOE-CAS-WHT-7'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-CAS-WHT-8', 'Casual Sneaker White - 8', 'Canvas Casual Sneaker White UK8', 'Pantaloons', 'ERP-PANT-042', 'PAIR', 495, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_CASUAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560042', true FROM products.products WHERE sku='SHOE-CAS-WHT-8'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-CAS-WHT-9', 'Casual Sneaker White - 9', 'Canvas Casual Sneaker White UK9', 'Pantaloons', 'ERP-PANT-043', 'PAIR', 510, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_CASUAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560043', true FROM products.products WHERE sku='SHOE-CAS-WHT-9'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-CAS-BLK-8', 'Casual Sneaker Black - 8', 'Canvas Casual Sneaker Black UK8', 'Pantaloons', 'ERP-PANT-044', 'PAIR', 495, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_CASUAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560044', true FROM products.products WHERE sku='SHOE-CAS-BLK-8'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-FRM-BRN-8', 'Formal Oxford Brown - 8', 'Leather Oxford Formal Shoe Brown UK8', 'Pantaloons', 'ERP-PANT-045', 'PAIR', 820, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_FORMAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560045', true FROM products.products WHERE sku='SHOE-FRM-BRN-8'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-FRM-BRN-9', 'Formal Oxford Brown - 9', 'Leather Oxford Formal Shoe Brown UK9', 'Pantaloons', 'ERP-PANT-046', 'PAIR', 840, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_FORMAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560046', true FROM products.products WHERE sku='SHOE-FRM-BRN-9'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-FRM-BLK-8', 'Formal Oxford Black - 8', 'Leather Oxford Formal Shoe Black UK8', 'Pantaloons', 'ERP-PANT-047', 'PAIR', 820, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_FORMAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560047', true FROM products.products WHERE sku='SHOE-FRM-BLK-8'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SHOE-FRM-BLK-9', 'Formal Oxford Black - 9', 'Leather Oxford Formal Shoe Black UK9', 'Pantaloons', 'ERP-PANT-048', 'PAIR', 840, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'FOOTWEAR_FORMAL'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560048', true FROM products.products WHERE sku='SHOE-FRM-BLK-9'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-BELT-BLK-S', 'Leather Belt Black - S', 'Genuine Leather Belt Black Small', 'Pantaloons', 'ERP-PANT-049', 'EACH', 145, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560049', true FROM products.products WHERE sku='ACC-BELT-BLK-S'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-BELT-BLK-M', 'Leather Belt Black - M', 'Genuine Leather Belt Black Medium', 'Pantaloons', 'ERP-PANT-050', 'EACH', 150, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560050', true FROM products.products WHERE sku='ACC-BELT-BLK-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-BELT-BRN-M', 'Leather Belt Brown - M', 'Genuine Leather Belt Brown Medium', 'Pantaloons', 'ERP-PANT-051', 'EACH', 150, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560051', true FROM products.products WHERE sku='ACC-BELT-BRN-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-SCARF-BLU', 'Scarf Blue', 'Woven Cotton Scarf Blue', 'Pantaloons', 'ERP-PANT-052', 'EACH', 95, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560052', true FROM products.products WHERE sku='ACC-SCARF-BLU'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-SCARF-RED', 'Scarf Red', 'Woven Cotton Scarf Red', 'Pantaloons', 'ERP-PANT-053', 'EACH', 95, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560053', true FROM products.products WHERE sku='ACC-SCARF-RED'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-WALLET-BLK', 'Wallet Black', 'Leather Bi-Fold Wallet Black', 'Pantaloons', 'ERP-PANT-054', 'EACH', 120, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560054', true FROM products.products WHERE sku='ACC-WALLET-BLK'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('ACC-WALLET-BRN', 'Wallet Brown', 'Leather Bi-Fold Wallet Brown', 'Pantaloons', 'ERP-PANT-055', 'EACH', 120, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560055', true FROM products.products WHERE sku='ACC-WALLET-BRN'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('JACK-DEN-BLU-M', 'Denim Jacket Blue - M', 'Classic Denim Jacket Blue Medium', 'Pantaloons', 'ERP-PANT-056', 'EACH', 780, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_OUTERWEAR'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560056', true FROM products.products WHERE sku='JACK-DEN-BLU-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('JACK-DEN-BLU-L', 'Denim Jacket Blue - L', 'Classic Denim Jacket Blue Large', 'Pantaloons', 'ERP-PANT-057', 'EACH', 800, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_OUTERWEAR'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560057', true FROM products.products WHERE sku='JACK-DEN-BLU-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('JACK-WIN-BLK-M', 'Windbreaker Black - M', 'Lightweight Windbreaker Black Medium', 'Pantaloons', 'ERP-PANT-058', 'EACH', 420, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_OUTERWEAR'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560058', true FROM products.products WHERE sku='JACK-WIN-BLK-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('JACK-WIN-BLK-L', 'Windbreaker Black - L', 'Lightweight Windbreaker Black Large', 'Pantaloons', 'ERP-PANT-059', 'EACH', 440, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_OUTERWEAR'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560059', true FROM products.products WHERE sku='JACK-WIN-BLK-L'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SOCK-CTN-WHT-M', 'Cotton Socks White - M', 'Ankle Length Cotton Socks White Medium', 'Pantaloons', 'ERP-PANT-060', 'PACK', 85, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'APPAREL_UNDERWEAR'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560060', true FROM products.products WHERE sku='SOCK-CTN-WHT-M'
ON CONFLICT (barcode_value) DO NOTHING;
INSERT INTO products.products (sku, name, description, brand, erp_product_code, unit_of_measure, weight_grams, is_rfid_enabled, is_active, category_id)
VALUES ('SUNGL-DEMO-001', 'Sunglasses Demo Tag', 'Demo RFID tag for testing - Sunglass series', 'Demo', 'ERP-DEMO-001', 'EACH', 45, true, true,
  (SELECT id FROM products.product_categories WHERE code = 'ACCESSORIES'))
ON CONFLICT (sku) DO UPDATE SET
  name=EXCLUDED.name, description=EXCLUDED.description, brand=EXCLUDED.brand,
  erp_product_code=EXCLUDED.erp_product_code, unit_of_measure=EXCLUDED.unit_of_measure,
  weight_grams=EXCLUDED.weight_grams, is_rfid_enabled=EXCLUDED.is_rfid_enabled,
  is_active=EXCLUDED.is_active, category_id=EXCLUDED.category_id, updated_at=now();
INSERT INTO products.barcodes (product_id, barcode_type, barcode_value, is_primary)
SELECT id, 'ean13', '8901234560099', true FROM products.products WHERE sku='SUNGL-DEMO-001'
ON CONFLICT (barcode_value) DO NOTHING;

COMMIT;

-- Verify:
SELECT COUNT(*) AS products_inserted FROM products.products;
SELECT COUNT(*) AS barcodes_inserted FROM products.barcodes;
