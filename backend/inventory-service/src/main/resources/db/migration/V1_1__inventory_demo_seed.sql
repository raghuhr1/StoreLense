-- =============================================================================
-- StoreLense: Seed realistic inventory state for demo.
--
-- inventory_state rows are created by the REST seed scripts when
-- /api/inventory/expected is called, but quantity_on_hand stays 0
-- until RFID reads flow through Kafka.  This migration patches those rows
-- to show 93-99 % accuracy so every page is immediately useful.
--
-- Also seeds epc_registry so EPC-summary stats on the Inventory page work.
--
-- Idempotent: only touches rows where quantity_on_hand = 0 and
-- quantity_expected > 0, and uses ON CONFLICT DO NOTHING for epc_registry.
-- =============================================================================

DO $$
DECLARE
  v_store_id   UUID;
  v_product_id UUID;
  v_expected   INT;
  v_on_hand    INT;
  v_accuracy   NUMERIC(5,2);
  v_h          INT;
BEGIN
  -- ── 1. Patch inventory_state: give every seeded product realistic on-hand ──
  FOR v_store_id, v_product_id, v_expected IN
    SELECT store_id, product_id, quantity_expected
    FROM   inventory.inventory_state
    WHERE  quantity_expected > 0
      AND  quantity_on_hand  = 0
  LOOP
    v_h       := abs(hashtext(v_store_id::text || v_product_id::text));
    -- variance 0–2 units lost/missing
    v_on_hand := GREATEST(0, v_expected - (v_h % 3));
    v_accuracy := CASE WHEN v_expected > 0
                       THEN ROUND((v_on_hand::numeric / v_expected) * 100, 2)
                       ELSE NULL END;

    UPDATE inventory.inventory_state
    SET    quantity_on_hand = v_on_hand,
           accuracy_pct     = v_accuracy,
           last_counted_at  = now() - interval '2 hours'
    WHERE  store_id   = v_store_id
      AND  product_id = v_product_id
      AND  (zone_id IS NULL);
  END LOOP;

  -- ── 2. Seed epc_registry from products.epc_tags for each store ─────────────
  -- For every EPC registered against a product that has expected inventory in a
  -- store, create an epc_registry entry (status = in_store).
  INSERT INTO inventory.epc_registry
    (epc, store_id, product_id, status, last_seen_at, first_seen_at)
  SELECT
    et.epc,
    ist.store_id,
    et.product_id,
    'in_store',
    now() - interval '2 hours',
    now() - interval '30 days'
  FROM products.epc_tags   et
  JOIN inventory.inventory_state ist
       ON  ist.product_id    = et.product_id
       AND ist.zone_id       IS NULL
  WHERE et.is_active          = true
    AND ist.quantity_expected > 0
  ON CONFLICT (epc, store_id) DO NOTHING;
END $$;
