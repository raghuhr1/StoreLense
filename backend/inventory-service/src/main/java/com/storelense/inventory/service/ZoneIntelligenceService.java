package com.storelense.inventory.service;

import com.storelense.inventory.dto.ProductFrequencyRow;
import com.storelense.inventory.dto.ZoneHealthSummary;
import com.storelense.inventory.dto.ZoneTrendPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoneIntelligenceService {

    private final JdbcClient jdbcClient;

    /**
     * Live per-zone health — product counts grouped by rollup status.
     * Uses the same CTE logic as ZoneScanRollupService.getLive() but
     * aggregates per zone rather than returning individual rows.
     */
    @Transactional(readOnly = true)
    public List<ZoneHealthSummary> getZoneHealth(UUID storeId) {
        return jdbcClient.sql("""
                WITH scan AS (
                    SELECT product_id, zone_id, COUNT(*) AS cnt
                    FROM inventory.epc_registry
                    WHERE store_id = :storeId AND status = 'in_store'
                    GROUP BY product_id, zone_id
                ),
                rollup AS (
                    SELECT
                        pl.zone_id,
                        CASE
                            WHEN COALESCE(s.cnt, 0) <= pl.min_qty THEN 'critical'
                            WHEN COALESCE(s.cnt, 0) <  pl.par_qty THEN 'low'
                            WHEN COALESCE(s.cnt, 0) >  pl.par_qty THEN 'surplus'
                            ELSE 'ok'
                        END AS status
                    FROM inventory.zone_par_levels pl
                    LEFT JOIN scan s
                           ON s.product_id = pl.product_id
                          AND s.zone_id    = pl.zone_id
                    WHERE pl.store_id = :storeId AND pl.active = true
                )
                SELECT
                    r.zone_id,
                    z.name                                               AS zone_name,
                    COUNT(*) FILTER (WHERE r.status = 'critical')::int  AS critical_count,
                    COUNT(*) FILTER (WHERE r.status = 'low')::int       AS low_count,
                    COUNT(*) FILTER (WHERE r.status = 'ok')::int        AS ok_count,
                    COUNT(*) FILTER (WHERE r.status = 'surplus')::int   AS surplus_count,
                    COUNT(*)::int                                        AS total_products
                FROM rollup r
                LEFT JOIN stores.zones z ON z.id = r.zone_id
                GROUP BY r.zone_id, z.name
                ORDER BY critical_count DESC, low_count DESC
                """)
                .param("storeId", storeId)
                .query((rs, n) -> new ZoneHealthSummary(
                        UUID.fromString(rs.getString("zone_id")),
                        rs.getString("zone_name"),
                        rs.getInt("critical_count"),
                        rs.getInt("low_count"),
                        rs.getInt("ok_count"),
                        rs.getInt("surplus_count"),
                        rs.getInt("total_products")
                ))
                .list();
    }

    /**
     * Top products by auto-triggered replenishment frequency.
     * Reads from refill.refill_tasks (source='soh_trigger') within the look-back window.
     */
    @Transactional(readOnly = true)
    public List<ProductFrequencyRow> getProductFrequency(UUID storeId, int days, int limit) {
        return jdbcClient.sql("""
                SELECT
                    i.product_id,
                    p.sku,
                    p.name                        AS product_name,
                    COUNT(DISTINCT t.id)::int     AS refill_count,
                    COALESCE(SUM(i.requested_quantity), 0)::int AS total_units_requested,
                    MAX(t.created_at)::text        AS last_refill_at
                FROM refill.refill_task_items i
                JOIN refill.refill_tasks t ON t.id = i.task_id
                LEFT JOIN products.products p ON p.id = i.product_id
                WHERE t.store_id  = :storeId
                  AND t.source    = 'soh_trigger'
                  AND t.created_at >= now() - make_interval(days => :days)
                GROUP BY i.product_id, p.sku, p.name
                ORDER BY refill_count DESC, total_units_requested DESC
                LIMIT :limit
                """)
                .param("storeId", storeId)
                .param("days",    days)
                .param("limit",   limit)
                .query((rs, n) -> new ProductFrequencyRow(
                        UUID.fromString(rs.getString("product_id")),
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getInt("refill_count"),
                        rs.getInt("total_units_requested"),
                        rs.getString("last_refill_at")
                ))
                .list();
    }

    /**
     * Daily rollup trend from persisted zone_scan_rollup snapshots.
     * Each row is one calendar day; counts reflect the snapshot taken on that day.
     * Optionally scoped to a single zone.
     */
    @Transactional(readOnly = true)
    public List<ZoneTrendPoint> getZoneTrend(UUID storeId, UUID zoneId, int days) {
        String sql = """
                SELECT
                    DATE_TRUNC('day', zsr.computed_at)::date::text       AS day,
                    COUNT(*) FILTER (WHERE zsr.status = 'critical')::int AS critical_count,
                    COUNT(*) FILTER (WHERE zsr.status = 'low')::int      AS low_count,
                    COUNT(*) FILTER (WHERE zsr.status = 'ok')::int       AS ok_count,
                    COUNT(*) FILTER (WHERE zsr.status = 'surplus')::int  AS surplus_count
                FROM inventory.zone_scan_rollup zsr
                WHERE zsr.store_id    = :storeId
                  AND zsr.computed_at >= now() - make_interval(days => :days)
                """ + (zoneId != null ? "  AND zsr.zone_id = :zoneId\n" : "") + """
                GROUP BY 1
                ORDER BY 1
                """;

        var spec = jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("days",    days);
        if (zoneId != null) spec = spec.param("zoneId", zoneId);

        return spec.query((rs, n) -> new ZoneTrendPoint(
                rs.getString("day"),
                rs.getInt("critical_count"),
                rs.getInt("low_count"),
                rs.getInt("ok_count"),
                rs.getInt("surplus_count")
        )).list();
    }
}
