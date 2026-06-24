-- StoreLense: Register real RFID EPCs into epc_registry
-- Generated: 2026-06-24 from Chainway C72 scan of physical tags
-- Run: psql -h localhost -U storelense_app -d storelense -f register_real_epcs.sql

BEGIN;

-- Step 1: Ensure SUNGL-DEMO-001 product exists
INSERT INTO products.products (sku, name, description, brand, erp_product_code, is_rfid_enabled, is_active)
VALUES ('SUNGL-DEMO-001','Sunglasses Demo Tag','Demo RFID tag for testing','Demo','ERP-DEMO-001',true,true)
ON CONFLICT (sku) DO NOTHING;

-- Step 2: Insert real EPC tags into epc_registry
-- One row per physical tag scanned from the real device

INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3007257BF400B71400000007',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-28'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3009257BF400B71400000009',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '300a257BF400B7140000000A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '300c257BF400B7140000000C',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '300d257BF400B7140000000D',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3012257BF400B71400000012',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3013257BF400B71400000013',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3014257BF400B71400000014',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3015257BF400B71400000015',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3017257BF400B71400000017',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3018257BF400B71400000018',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '301b257BF400B7140000001B',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '301c257BF400B7140000001C',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3020257BF400B71400000020',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3022257BF400B71400000022',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-XL'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3023257BF400B71400000023',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3024257BF400B71400000024',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3025257BF400B71400000025',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3026257BF400B71400000026',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3028257BF400B71400000028',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '302a257BF400B7140000002A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '302b257BF400B7140000002B',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '302c257BF400B7140000002C',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-NVY-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3030257BF400B71400000030',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3033257BF400B71400000033',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3034257BF400B71400000034',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3039257BF400B71400000039',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30395DFA82CEDAC00001A88A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30395DFA82D061C0000C55E3',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30395DFA82FABF80002C6984',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30395DFA82FAC1C0007546F5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '303ACA498179F1199C82DEBE',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '303a257BF400B7140000003A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '303e257BF400B7140000003E',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '303f257BF400B7140000003F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-28'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3043257BF400B71400000043',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3048257BF400B71400000048',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3049257BF400B71400000049',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '304a257BF400B7140000004A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '304b257BF400B7140000004B',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '304d257BF400B7140000004D',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3054257BF400B71400000054',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3055257BF400B71400000055',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3057257BF400B71400000057',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '305b257BF400B7140000005B',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '305e257BF400B7140000005E',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '305f257BF400B7140000005F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3061257BF400B71400000061',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3062257BF400B71400000062',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-XL'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3063257BF400B71400000063',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3066257BF400B71400000066',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '306a257BF400B7140000006A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '306c257BF400B7140000006C',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '306e257BF400B7140000006E',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '306f257BF400B7140000006F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3070257BF400B71400000070',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3072257BF400B71400000072',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-NVY-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3073257BF400B71400000073',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3074257BF400B71400000074',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3076257BF400B71400000076',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3078257BF400B71400000078',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3079257BF400B71400000079',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '307f257BF400B7140000007F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3083257BF400B71400000083',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3084257BF400B71400000084',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3085257BF400B71400000085',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3087257BF400B71400000087',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3088257BF400B71400000088',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3089257BF400B71400000089',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-28'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '308a257BF400B7140000008A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '308d257BF400B7140000008D',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '308e257BF400B7140000008E',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '308f257BF400B7140000008F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3090257BF400B71400000090',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3092257BF400B71400000092',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3093257BF400B71400000093',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3094257BF400B71400000094',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-KHA-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3095257BF400B71400000095',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3097257BF400B71400000097',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TROP-CHI-NAV-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '3098257BF400B71400000098',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '309a257BF400B7140000009A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '309b257BF400B7140000009B',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '309f257BF400B7140000009F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-WHT-XL'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a0257BF400B714000000A0',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a2257BF400B714000000A2',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SHRT-FRM-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a5257BF400B714000000A5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-S'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a6257BF400B714000000A6',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a7257BF400B714000000A7',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a8257BF400B714000000A8',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30a9257BF400B714000000A9',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30ab257BF400B714000000AB',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='TSHRT-CRW-NVY-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30ac257BF400B714000000AC',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30ad257BF400B714000000AD',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30af257BF400B714000000AF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='POLO-SLM-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30b3257BF400B714000000B3',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30b5257BF400B714000000B5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-WHT-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30b7257BF400B714000000B7',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30b8257BF400B714000000B8',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='KURTA-CTN-BEI-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30ba257BF400B714000000BA',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30bb257BF400B714000000BB',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-DEN-BLU-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30bc257BF400B714000000BC',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30bd257BF400B714000000BD',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='JACK-WIN-BLK-L'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30be257BF400B714000000BE',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-28'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30c1257BF400B714000000C1',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30c4257BF400B714000000C4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-32'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30c6257BF400B714000000C6',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLU-34'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '30c7257BF400B714000000C7',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='PANT-DEN-BLK-30'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303031',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303032',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303033',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303034',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303035',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303036',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303037',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303038',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303039',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303130',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303131',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303132',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303133',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303135',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303136',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303137',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303138',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303139',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303230',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303231',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303232',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303234',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303235',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303236',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303237',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303238',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303239',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303330',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303331',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303332',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303333',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303334',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303335',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303336',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303337',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303338',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303339',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303430',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303431',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303432',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303433',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303434',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303435',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303436',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303437',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303438',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303439',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303530',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303531',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303532',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303533',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303534',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303535',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303536',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303537',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303538',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303539',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303630',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303632',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303633',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303634',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303635',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303636',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303637',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303638',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303639',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303730',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303731',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303732',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303733',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303734',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303736',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303738',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303739',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303830',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303831',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303832',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303833',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303834',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303835',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303836',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303837',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303838',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303839',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303931',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303932',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303933',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303934',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303936',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303937',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303938',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330303939',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737330313030',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737331323334',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737331323335',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C6173733132333620202020',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E676C61737331323337',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT '53756E67A50300636F709709',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='SUNGL-DEMO-001'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26C831F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26DD7BF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26F572F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26F83DF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26FE83F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A26FE8DF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A2702D4F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A2702DEF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A270923F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A270924F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A27092DF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A27092EF',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A270B73F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A5030061A270B74F',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C505E4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C5AFD4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C5AFF4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C5B4C5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C5B4E5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C5C015',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C62554',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C62584',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C625B5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C625D5',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C68A34',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C68A65',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C68AA4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C6EF34',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C71404',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C714B4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C765D4',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340C7EA54',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340CAEAA8',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340CCCCB3',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A503006340CCCCD3',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6D3748',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6D3758',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6D3788',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6D3798',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D19',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D58',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D59',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D69',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D89',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0D8A',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0DAA',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E0DBA',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E7269',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6E97C9',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BLK-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6EA329',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-BELT-BRN-M'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F6FD759',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-BLU'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F709719',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-SCARF-RED'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F70B2D9',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BLK'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();
INSERT INTO inventory.epc_registry (epc, store_id, product_id, zone_id, status, first_seen_at, created_at, updated_at)
SELECT 'E2801191A50300636F70B2E9',
  (SELECT id FROM stores.stores WHERE store_code='LK001'),
  (SELECT id FROM products.products WHERE sku='ACC-WALLET-BRN'),
  (SELECT id FROM stores.zones WHERE zone_code='SALES_FLOOR' AND store_id=(SELECT id FROM stores.stores WHERE store_code='LK001')),
  'in_store', now(), now(), now()
ON CONFLICT (epc, store_id) DO UPDATE SET product_id=EXCLUDED.product_id, status='in_store', updated_at=now();

COMMIT;

-- Verify:
SELECT COUNT(*) as registered_epcs FROM inventory.epc_registry WHERE store_id=(SELECT id FROM stores.stores WHERE store_code='LK001');
