-- =============================================================================
-- StoreLense: Seed 30 days of realistic demo KPI data for all active stores.
-- Uses hashtext() for deterministic variation so every store/date combo
-- produces consistent values across restarts.  ON CONFLICT DO NOTHING makes
-- this safe to apply against a DB that already has production data.
-- =============================================================================

DO $$
DECLARE
  v_store_id   UUID;
  v_day        INT;
  v_date       DATE;
  v_h          INT;   -- hash seed
  v_accuracy   NUMERIC(5,2);
  v_sessions   INT;
  v_rc         INT;
  v_rd         INT;
  v_reads      BIGINT;
  v_skus       INT;
  v_variances  INT;
BEGIN
  FOR v_store_id IN
    SELECT id FROM stores.stores WHERE is_active = true
  LOOP
    FOR v_day IN 1..30 LOOP
      v_date := CURRENT_DATE - v_day;

      -- Per-store-per-day hash seed for deterministic variation
      v_h         := abs(hashtext(v_store_id::text || v_date::text));

      v_accuracy  := ROUND((93 + (v_h % 7))::numeric, 2);
      v_sessions  := 1 + (v_h % 3);
      v_rc        := 3 + ((v_h / 10) % 8);
      v_rd        := GREATEST(0, v_rc - ((v_h / 100) % 3));
      v_reads     := 600 + (v_h % 1400)::bigint;
      v_skus      := 8  + (v_h % 10);
      v_variances := (v_h / 7) % 5;

      INSERT INTO reporting.kpi_daily (
        store_id, kpi_date,
        inventory_accuracy_pct,
        soh_sessions_count,
        refill_tasks_created, refill_tasks_completed,
        refill_completion_rate_pct,
        total_epc_reads, unique_skus_counted, variance_items_count
      ) VALUES (
        v_store_id,
        v_date,
        v_accuracy,
        v_sessions,
        v_rc,
        v_rd,
        CASE WHEN v_rc > 0
             THEN ROUND((v_rd::numeric / v_rc) * 100, 2)
             ELSE NULL END,
        v_reads,
        v_skus,
        v_variances
      )
      ON CONFLICT (store_id, kpi_date) DO NOTHING;
    END LOOP;
  END LOOP;
END $$;
