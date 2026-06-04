-- =============================================================================
-- StoreLense: Utility Functions
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Partition maintenance: create next month's rfid_reads and audit_log partitions.
-- Schedule via pg_cron: SELECT cron.schedule('0 0 1 * *', 'SELECT fn_create_monthly_partitions()');
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_create_monthly_partitions()
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    next_month_start DATE := date_trunc('month', now() + INTERVAL '1 month');
    next_month_end   DATE := next_month_start + INTERVAL '1 month';
    partition_suffix TEXT := to_char(next_month_start, 'YYYY_MM');
    rfid_part_name   TEXT := 'rfid_reads_' || partition_suffix;
    audit_part_name  TEXT := 'audit_log_'  || partition_suffix;
BEGIN
    -- rfid.rfid_reads
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'rfid' AND c.relname = rfid_part_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE rfid.%I PARTITION OF rfid.rfid_reads
             FOR VALUES FROM (%L) TO (%L)',
            rfid_part_name, next_month_start, next_month_end
        );
        RAISE NOTICE 'Created partition rfid.%', rfid_part_name;
    END IF;

    -- audit.audit_log
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'audit' AND c.relname = audit_part_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE audit.%I PARTITION OF audit.audit_log
             FOR VALUES FROM (%L) TO (%L)',
            audit_part_name, next_month_start, next_month_end
        );
        RAISE NOTICE 'Created partition audit.%', audit_part_name;
    END IF;
END;
$$;

-- ----------------------------------------------------------------------------
-- Archive old partitions (older than retention_months).
-- Detaches from parent so they can be exported to S3 and dropped.
-- Call manually or via scheduled job before dropping.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_archive_old_rfid_partition(
    p_retention_months INT DEFAULT 12
)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    cutoff_month     DATE := date_trunc('month', now() - (p_retention_months || ' months')::INTERVAL);
    partition_name   TEXT := 'rfid_reads_' || to_char(cutoff_month, 'YYYY_MM');
    result_msg       TEXT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'rfid' AND c.relname = partition_name
    ) THEN
        EXECUTE format('ALTER TABLE rfid.rfid_reads DETACH PARTITION rfid.%I', partition_name);
        result_msg := 'Detached partition: rfid.' || partition_name || '. Ready for S3 export.';
    ELSE
        result_msg := 'Partition not found: rfid.' || partition_name;
    END IF;
    RETURN result_msg;
END;
$$;

-- ----------------------------------------------------------------------------
-- Recalculate inventory_state.accuracy_pct for a store after SOH session.
-- Called by soh-service after committing soh_results.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inventory.fn_recalc_accuracy(p_store_id UUID)
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    rows_updated INT;
BEGIN
    WITH latest_counts AS (
        SELECT
            si.product_id,
            si.zone_id,
            si.counted_quantity,
            si.expected_quantity,
            CASE
                WHEN si.expected_quantity = 0 AND si.counted_quantity = 0 THEN 100.0
                WHEN si.expected_quantity = 0 THEN 0.0
                ELSE ROUND(100.0 * si.counted_quantity / si.expected_quantity, 2)
            END AS calc_accuracy,
            s.id     AS session_id,
            s.completed_at
        FROM soh.soh_session_items si
        JOIN soh.soh_sessions s ON s.id = si.session_id
        WHERE s.store_id  = p_store_id
          AND s.status    = 'completed'
          AND s.completed_at = (
              SELECT MAX(s2.completed_at)
              FROM soh.soh_sessions s2
              WHERE s2.store_id = p_store_id AND s2.status = 'completed'
          )
    )
    UPDATE inventory.inventory_state ist
    SET
        quantity_on_hand    = lc.counted_quantity,
        quantity_expected   = lc.expected_quantity,
        accuracy_pct        = lc.calc_accuracy,
        last_counted_at     = lc.completed_at,
        last_soh_session_id = lc.session_id,
        updated_at          = now()
    FROM latest_counts lc
    WHERE ist.store_id   = p_store_id
      AND ist.product_id = lc.product_id
      AND ist.zone_id IS NOT DISTINCT FROM lc.zone_id;

    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    RETURN rows_updated;
END;
$$;

-- ----------------------------------------------------------------------------
-- Calculate kpi_daily row for a given store and date.
-- Called nightly by reporting-service.
-- Uses INSERT ... ON CONFLICT DO UPDATE (upsert).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION reporting.fn_upsert_kpi_daily(
    p_store_id UUID,
    p_date     DATE
)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_accuracy          NUMERIC(5,2);
    v_soh_sessions      INT;
    v_tasks_created     INT;
    v_tasks_completed   INT;
    v_completion_rate   NUMERIC(5,2);
    v_avg_refill_mins   NUMERIC(8,2);
    v_total_reads       BIGINT;
    v_unique_skus       INT;
    v_variance_items    INT;
BEGIN
    -- SOH accuracy (average over sessions completed on this date)
    SELECT ROUND(AVG(r.accuracy_pct), 2), COUNT(DISTINCT s.id)
    INTO v_accuracy, v_soh_sessions
    FROM soh.soh_results r
    JOIN soh.soh_sessions s ON s.id = r.session_id
    WHERE s.store_id     = p_store_id
      AND s.completed_at::DATE = p_date
      AND s.status       = 'completed';

    -- Unique SKUs counted
    SELECT COUNT(DISTINCT product_id)
    INTO v_unique_skus
    FROM soh.soh_session_items si
    JOIN soh.soh_sessions s ON s.id = si.session_id
    WHERE s.store_id = p_store_id
      AND s.completed_at::DATE = p_date
      AND s.status = 'completed';

    -- Variance items
    SELECT COUNT(*)
    INTO v_variance_items
    FROM soh.soh_variance v
    JOIN soh.soh_sessions s ON s.id = v.session_id
    WHERE s.store_id = p_store_id
      AND s.completed_at::DATE = p_date
      AND v.variance_qty != 0;

    -- Refill KPIs
    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE status = 'completed')
    INTO v_tasks_created, v_tasks_completed
    FROM refill.refill_tasks
    WHERE store_id    = p_store_id
      AND created_at::DATE = p_date;

    v_completion_rate := CASE
        WHEN v_tasks_created = 0 THEN NULL
        ELSE ROUND(100.0 * v_tasks_completed / v_tasks_created, 2)
    END;

    SELECT ROUND(AVG(
        EXTRACT(EPOCH FROM (a.completed_at - a.started_at)) / 60.0
    ), 2)
    INTO v_avg_refill_mins
    FROM refill.refill_assignments a
    JOIN refill.refill_tasks t ON t.id = a.task_id
    WHERE t.store_id         = p_store_id
      AND a.completed_at::DATE = p_date
      AND a.status           = 'completed';

    -- RFID read count
    SELECT COUNT(*)
    INTO v_total_reads
    FROM rfid.rfid_reads
    WHERE store_id        = p_store_id
      AND read_at::DATE   = p_date;

    INSERT INTO reporting.kpi_daily (
        store_id, kpi_date,
        inventory_accuracy_pct, soh_sessions_count,
        refill_tasks_created, refill_tasks_completed, refill_completion_rate_pct,
        avg_refill_time_minutes, total_epc_reads, unique_skus_counted, variance_items_count
    ) VALUES (
        p_store_id, p_date,
        v_accuracy, COALESCE(v_soh_sessions, 0),
        COALESCE(v_tasks_created, 0), COALESCE(v_tasks_completed, 0), v_completion_rate,
        v_avg_refill_mins, COALESCE(v_total_reads, 0), COALESCE(v_unique_skus, 0),
        COALESCE(v_variance_items, 0)
    )
    ON CONFLICT (store_id, kpi_date) DO UPDATE SET
        inventory_accuracy_pct     = EXCLUDED.inventory_accuracy_pct,
        soh_sessions_count         = EXCLUDED.soh_sessions_count,
        refill_tasks_created       = EXCLUDED.refill_tasks_created,
        refill_tasks_completed     = EXCLUDED.refill_tasks_completed,
        refill_completion_rate_pct = EXCLUDED.refill_completion_rate_pct,
        avg_refill_time_minutes    = EXCLUDED.avg_refill_time_minutes,
        total_epc_reads            = EXCLUDED.total_epc_reads,
        unique_skus_counted        = EXCLUDED.unique_skus_counted,
        variance_items_count       = EXCLUDED.variance_items_count;
END;
$$;
