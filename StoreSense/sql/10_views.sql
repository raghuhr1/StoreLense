-- =============================================================================
-- StoreLense: Materialized Views & Reporting Views
-- Refreshed nightly by reporting-service scheduler.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- mv_store_accuracy_7d
-- Rolling 7-day inventory accuracy per store.
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_store_accuracy_7d AS
SELECT
    r.store_id,
    ROUND(AVG(r.accuracy_pct), 2)                       AS avg_accuracy_pct,
    MIN(r.accuracy_pct)                                  AS min_accuracy_pct,
    MAX(r.accuracy_pct)                                  AS max_accuracy_pct,
    COUNT(*)                                             AS session_count,
    SUM(r.total_units_counted)                           AS total_units_counted,
    SUM(r.total_units_expected)                          AS total_units_expected,
    now()                                                AS refreshed_at
FROM soh.soh_results r
JOIN soh.soh_sessions s ON s.id = r.session_id
WHERE s.completed_at >= now() - INTERVAL '7 days'
  AND s.status = 'completed'
GROUP BY r.store_id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_store_accuracy_7d_store
    ON reporting.mv_store_accuracy_7d (store_id);

-- ----------------------------------------------------------------------------
-- mv_refill_kpi_30d
-- 30-day refill KPIs per store: completion rate, avg time, backlog.
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_refill_kpi_30d AS
SELECT
    t.store_id,
    COUNT(*)                                                     AS tasks_created,
    COUNT(*) FILTER (WHERE t.status = 'completed')               AS tasks_completed,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE t.status = 'completed')
        / NULLIF(COUNT(*), 0), 2
    )                                                            AS completion_rate_pct,
    ROUND(
        AVG(
            EXTRACT(EPOCH FROM (a.completed_at - a.started_at)) / 60.0
        ) FILTER (WHERE a.completed_at IS NOT NULL), 2
    )                                                            AS avg_task_time_minutes,
    COUNT(*) FILTER (WHERE t.status NOT IN ('completed', 'cancelled')) AS backlog_count,
    ROUND(AVG(t.priority), 2)                                    AS avg_priority,
    now()                                                        AS refreshed_at
FROM refill.refill_tasks t
LEFT JOIN refill.refill_assignments a ON a.task_id = t.id AND a.status = 'completed'
WHERE t.created_at >= now() - INTERVAL '30 days'
GROUP BY t.store_id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_refill_kpi_30d_store
    ON reporting.mv_refill_kpi_30d (store_id);

-- ----------------------------------------------------------------------------
-- mv_product_variance_30d
-- Top variance products per store over 30 days. Used for shrinkage analysis.
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_product_variance_30d AS
SELECT
    v.store_id,
    v.product_id,
    COUNT(DISTINCT v.session_id)                         AS session_count,
    SUM(ABS(v.variance_qty))                             AS total_abs_variance,
    SUM(v.variance_qty)                                  AS net_variance,
    ROUND(AVG(ABS(v.variance_pct)), 2)                   AS avg_abs_variance_pct,
    COUNT(*) FILTER (WHERE v.variance_type = 'undercount') AS undercount_occurrences,
    COUNT(*) FILTER (WHERE v.variance_type = 'overcount')  AS overcount_occurrences,
    now()                                                AS refreshed_at
FROM soh.soh_variance v
JOIN soh.soh_sessions s ON s.id = v.session_id
WHERE s.completed_at >= now() - INTERVAL '30 days'
  AND v.variance_qty != 0
GROUP BY v.store_id, v.product_id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_product_variance_30d_key
    ON reporting.mv_product_variance_30d (store_id, product_id);

CREATE INDEX IF NOT EXISTS idx_mv_product_variance_30d_store_abs
    ON reporting.mv_product_variance_30d (store_id, total_abs_variance DESC);

-- ----------------------------------------------------------------------------
-- mv_epc_inventory_summary
-- Current on-hand summary by store — joins epc_registry counts with inventory_state.
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS reporting.mv_epc_inventory_summary AS
SELECT
    er.store_id,
    er.product_id,
    COUNT(*) FILTER (WHERE er.status = 'in_store')   AS epc_in_store,
    COUNT(*) FILTER (WHERE er.status = 'missing')    AS epc_missing,
    COUNT(*) FILTER (WHERE er.status = 'sold')       AS epc_sold,
    COUNT(*) FILTER (WHERE er.status = 'damaged')    AS epc_damaged,
    MAX(er.last_seen_at)                             AS last_seen_at,
    now()                                            AS refreshed_at
FROM inventory.epc_registry er
GROUP BY er.store_id, er.product_id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_epc_inventory_summary_key
    ON reporting.mv_epc_inventory_summary (store_id, product_id);

-- ----------------------------------------------------------------------------
-- Refresh function — called by reporting-service nightly scheduler
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION reporting.fn_refresh_all_materialized_views()
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY reporting.mv_store_accuracy_7d;
    REFRESH MATERIALIZED VIEW CONCURRENTLY reporting.mv_refill_kpi_30d;
    REFRESH MATERIALIZED VIEW CONCURRENTLY reporting.mv_product_variance_30d;
    REFRESH MATERIALIZED VIEW CONCURRENTLY reporting.mv_epc_inventory_summary;
    RAISE NOTICE 'All materialized views refreshed at %', now();
END;
$$;
