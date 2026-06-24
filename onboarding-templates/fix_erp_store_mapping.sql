-- Fix: re-insert erp_store_mapping handling both unique constraints
BEGIN;

-- Clear any partial mapping entries for our 10 store codes
DELETE FROM erp.erp_store_mapping
WHERE internal_store_id IN (SELECT id FROM stores.stores WHERE store_code IN ('LK001','LK002','LK003','LK004','LK005','LK006','LK007','LK008','LK009','LK010'));

-- Re-insert with correct conflict target (internal_store_id)
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK001', id, true, true FROM stores.stores WHERE store_code='LK001'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK002', id, true, true FROM stores.stores WHERE store_code='LK002'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK003', id, true, true FROM stores.stores WHERE store_code='LK003'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK004', id, true, true FROM stores.stores WHERE store_code='LK004'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK005', id, true, true FROM stores.stores WHERE store_code='LK005'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK006', id, true, true FROM stores.stores WHERE store_code='LK006'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK007', id, true, true FROM stores.stores WHERE store_code='LK007'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK008', id, true, true FROM stores.stores WHERE store_code='LK008'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK009', id, true, true FROM stores.stores WHERE store_code='LK009'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK010', id, true, true FROM stores.stores WHERE store_code='LK010'
ON CONFLICT (internal_store_id) DO UPDATE
  SET erp_store_code=EXCLUDED.erp_store_code, updated_at=now();

COMMIT;

-- Verify:
SELECT m.erp_store_code, s.store_code, s.name
FROM erp.erp_store_mapping m
JOIN stores.stores s ON s.id = m.internal_store_id
ORDER BY s.store_code;
