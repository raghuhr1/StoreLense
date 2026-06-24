-- StoreLense: Provision 10 stores + zones + ERP store mapping
-- Run: docker exec -i <postgres> psql -U storelense_app -d storelense -f /tmp/import_stores.sql

BEGIN;

-- Step 1: Insert stores
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK001','Pantaloons Lajpat Nagar','Shop 12 Central Market','Lajpat Nagar II','New Delhi','Delhi','110024','IN','Asia/Kolkata','LK001',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK002','Pantaloons Connaught Place','Block A Inner Circle','Connaught Place','New Delhi','Delhi','110001','IN','Asia/Kolkata','LK002',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK003','Pantaloons Bandra','Linking Road','Bandra West','Mumbai','Maharashtra','400050','IN','Asia/Kolkata','LK003',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK004','Pantaloons Andheri','Infiniti Mall Level 2','Andheri West','Mumbai','Maharashtra','400053','IN','Asia/Kolkata','LK004',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK005','Pantaloons Koramangala','Forum Mall 3rd Floor','Koramangala','Bangalore','Karnataka','560095','IN','Asia/Kolkata','LK005',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK006','Pantaloons Indiranagar','100 Feet Road','Indiranagar','Bangalore','Karnataka','560038','IN','Asia/Kolkata','LK006',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK007','Pantaloons T Nagar','Usman Road','T Nagar','Chennai','Tamil Nadu','600017','IN','Asia/Kolkata','LK007',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK008','Pantaloons Ameerpet','SR Nagar Main Road','Ameerpet','Hyderabad','Telangana','500016','IN','Asia/Kolkata','LK008',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK009','Pantaloons Salt Lake','Sector V City Centre','Salt Lake','Kolkata','West Bengal','700091','IN','Asia/Kolkata','LK009',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();
INSERT INTO stores.stores (store_code, name, address_line1, address_line2, city, state_province, postal_code, country_code, timezone, erp_store_code, is_active)
VALUES ('LK010','Pantaloons CG Road','CG Road','Navrangpura','Ahmedabad','Gujarat','380009','IN','Asia/Kolkata','LK010',true)
ON CONFLICT (store_code) DO UPDATE SET
  name=EXCLUDED.name, address_line1=EXCLUDED.address_line1, erp_store_code=EXCLUDED.erp_store_code, updated_at=now();

-- Step 2: Insert zones
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK001'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK001'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK001'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK002'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK002'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK003'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK003'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK003'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK004'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK004'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK004'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK005'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK005'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK005'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK006'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK006'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK007'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK007'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK007'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK008'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK008'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK008'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK009'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK009'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'FITTING_ROOM', 'Fitting Rooms', 'fitting_room', true FROM stores.stores WHERE store_code='LK009'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'SALES_FLOOR', 'Sales Floor', 'floor', true FROM stores.stores WHERE store_code='LK010'
ON CONFLICT (store_id, zone_code) DO NOTHING;
INSERT INTO stores.zones (store_id, zone_code, name, zone_type, is_active)
SELECT id, 'BACK_ROOM', 'Back Room', 'backroom', true FROM stores.stores WHERE store_code='LK010'
ON CONFLICT (store_id, zone_code) DO NOTHING;

-- Step 3: Register ERP store mapping (needed for CSV upload to work)
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK001', id, true, true FROM stores.stores WHERE store_code='LK001'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK002', id, true, true FROM stores.stores WHERE store_code='LK002'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK003', id, true, true FROM stores.stores WHERE store_code='LK003'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK004', id, true, true FROM stores.stores WHERE store_code='LK004'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK005', id, true, true FROM stores.stores WHERE store_code='LK005'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK006', id, true, true FROM stores.stores WHERE store_code='LK006'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK007', id, true, true FROM stores.stores WHERE store_code='LK007'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK008', id, true, true FROM stores.stores WHERE store_code='LK008'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK009', id, true, true FROM stores.stores WHERE store_code='LK009'
ON CONFLICT (erp_store_code) DO NOTHING;
INSERT INTO erp.erp_store_mapping (erp_store_code, internal_store_id, inventory_sync_enabled, soh_push_enabled)
SELECT 'LK010', id, true, true FROM stores.stores WHERE store_code='LK010'
ON CONFLICT (erp_store_code) DO NOTHING;

COMMIT;

-- Verify:
SELECT store_code, name FROM stores.stores ORDER BY store_code;
SELECT COUNT(*) AS zones FROM stores.zones;
SELECT erp_store_code, internal_store_id FROM erp.erp_store_mapping;
