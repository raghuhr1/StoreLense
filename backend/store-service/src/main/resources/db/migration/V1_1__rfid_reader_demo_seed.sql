-- =============================================================================
-- StoreLense: Seed demo RFID readers for every active store.
-- 2 fixed readers (floor zones) + 1 handheld per store.
-- Idempotent: ON CONFLICT DO NOTHING on (store_id, reader_code).
-- =============================================================================

DO $$
DECLARE
  v_store_id   UUID;
  v_floor_zone UUID;
  v_back_zone  UUID;
  v_seq        INT := 1;
BEGIN
  FOR v_store_id IN
    SELECT id FROM stores.stores WHERE is_active = true ORDER BY created_at
  LOOP
    -- Pick up zone IDs (floor and backroom) for this store if available
    SELECT id INTO v_floor_zone
    FROM stores.zones
    WHERE store_id = v_store_id AND zone_type = 'floor'
    ORDER BY display_order LIMIT 1;

    SELECT id INTO v_back_zone
    FROM stores.zones
    WHERE store_id = v_store_id AND zone_type IN ('backroom','stockroom')
    ORDER BY display_order LIMIT 1;

    -- Fixed reader on the sales floor
    INSERT INTO stores.rfid_readers
      (store_id, zone_id, reader_code, reader_type, ip_address,
       firmware_version, antenna_count, tx_power_dbm, is_active, last_heartbeat_at)
    VALUES
      (v_store_id, v_floor_zone,
       'FXD-' || LPAD(v_seq::text, 3, '0') || '-FLOOR',
       'fixed',
       ('192.168.10.' || (10 + v_seq))::inet,
       'R700-3.4.1',
       4, 30.00, true,
       now() - interval '2 minutes')
    ON CONFLICT (store_id, reader_code) DO NOTHING;

    -- Fixed reader in the backroom
    INSERT INTO stores.rfid_readers
      (store_id, zone_id, reader_code, reader_type, ip_address,
       firmware_version, antenna_count, tx_power_dbm, is_active, last_heartbeat_at)
    VALUES
      (v_store_id, v_back_zone,
       'FXD-' || LPAD(v_seq::text, 3, '0') || '-BACK',
       'fixed',
       ('192.168.10.' || (50 + v_seq))::inet,
       'R700-3.4.1',
       2, 27.00, true,
       now() - interval '3 minutes')
    ON CONFLICT (store_id, reader_code) DO NOTHING;

    -- Handheld (no fixed IP)
    INSERT INTO stores.rfid_readers
      (store_id, zone_id, reader_code, reader_type,
       firmware_version, antenna_count, tx_power_dbm, is_active, last_heartbeat_at)
    VALUES
      (v_store_id, NULL,
       'HH-' || LPAD(v_seq::text, 3, '0') || '-A',
       'handheld',
       'RFD8500-2.1.0',
       1, 23.50, true,
       now() - interval '90 minutes')
    ON CONFLICT (store_id, reader_code) DO NOTHING;

    v_seq := v_seq + 1;
  END LOOP;
END $$;
