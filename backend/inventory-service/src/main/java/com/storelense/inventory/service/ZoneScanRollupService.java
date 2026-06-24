package com.storelense.inventory.service;

import com.storelense.inventory.domain.entity.ZoneScanRollup;
import com.storelense.inventory.domain.repository.ZoneScanRollupRepository;
import com.storelense.inventory.dto.ZoneScanRollupRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoneScanRollupService {

    private final ZoneScanRollupRepository repository;
    private final JdbcClient jdbcClient;

    // ── LIVE query — always reflects current epc_registry state ───────────────

    @Transactional(readOnly = true)
    public List<ZoneScanRollupRow> getLive(UUID storeId, UUID zoneId) {
        String sql = """
                SELECT
                    gen_random_uuid()               AS id,
                    pl.store_id,
                    pl.zone_id,
                    z.name                          AS zone_name,
                    pl.product_id,
                    p.sku,
                    p.name                          AS product_name,
                    NULL::uuid                      AS session_id,
                    COALESCE(scan.cnt, 0)           AS scanned_qty,
                    pl.par_qty,
                    pl.min_qty,
                    COALESCE(scan.cnt, 0) - pl.par_qty AS variance,
                    CASE
                        WHEN COALESCE(scan.cnt, 0) <= pl.min_qty THEN 'critical'
                        WHEN COALESCE(scan.cnt, 0) <  pl.par_qty THEN 'low'
                        WHEN COALESCE(scan.cnt, 0) >  pl.par_qty THEN 'surplus'
                        ELSE 'ok'
                    END                             AS status,
                    now()::text                     AS computed_at
                FROM inventory.zone_par_levels pl
                LEFT JOIN stores.zones z
                    ON z.id = pl.zone_id
                LEFT JOIN products.products p
                    ON p.id = pl.product_id
                LEFT JOIN (
                    SELECT product_id, zone_id, COUNT(*) AS cnt
                    FROM inventory.epc_registry
                    WHERE store_id = :storeId AND status = 'in_store'
                    GROUP BY product_id, zone_id
                ) scan ON scan.product_id = pl.product_id
                       AND scan.zone_id   = pl.zone_id
                WHERE pl.store_id = :storeId
                  AND pl.active   = true
                """ + (zoneId != null ? "  AND pl.zone_id = :zoneId\n" : "") + """
                ORDER BY
                    CASE status
                        WHEN 'critical' THEN 1
                        WHEN 'low'      THEN 2
                        WHEN 'ok'       THEN 3
                        ELSE                 4
                    END,
                    variance ASC
                """;

        var spec = jdbcClient.sql(sql).param("storeId", storeId);
        if (zoneId != null) spec = spec.param("zoneId", zoneId);
        return spec.query(this::mapRow).list();
    }

    // ── COMPUTE + PERSIST — snapshot tied to a SOH session ───────────────────

    @SuppressWarnings("null")
    @Transactional
    public List<ZoneScanRollupRow> computeAndSave(UUID storeId, UUID sessionId) {
        List<ZoneScanRollupRow> rows = getLive(storeId, null);

        // Delete any prior rollup for this session before re-saving
        if (sessionId != null) {
            repository.deleteAll(
                repository.findByStoreIdAndSessionIdOrderByStatusAscVarianceAsc(storeId, sessionId)
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ZoneScanRollup> entities = rows.stream().map(r ->
            ZoneScanRollup.builder()
                .storeId(storeId)
                .zoneId(r.zoneId())
                .productId(r.productId())
                .sessionId(sessionId)
                .scannedQty(r.scannedQty())
                .parQty(r.parQty())
                .minQty(r.minQty())
                .variance(r.variance())
                .status(r.status())
                .computedAt(now)
                .build()
        ).toList();
        repository.saveAll(entities);

        // Return the enriched rows (with zone/product names) rather than bare entities
        return rows.stream().map(r -> new ZoneScanRollupRow(
            r.id(), storeId, r.zoneId(), r.zoneName(), r.productId(),
            r.sku(), r.productName(), sessionId,
            r.scannedQty(), r.parQty(), r.minQty(), r.variance(),
            r.status(), now.toString()
        )).toList();
    }

    // ── GET persisted rollup for a past session ───────────────────────────────

    @Transactional(readOnly = true)
    public List<ZoneScanRollupRow> getBySession(UUID storeId, UUID sessionId) {
        // Enrich with zone + product names via a JOIN
        return jdbcClient.sql("""
                SELECT
                    sr.id, sr.store_id, sr.zone_id,
                    z.name   AS zone_name,
                    sr.product_id,
                    p.sku,
                    p.name   AS product_name,
                    sr.session_id,
                    sr.scanned_qty, sr.par_qty, sr.min_qty, sr.variance,
                    sr.status,
                    sr.computed_at::text AS computed_at
                FROM inventory.zone_scan_rollup sr
                LEFT JOIN stores.zones z      ON z.id = sr.zone_id
                LEFT JOIN products.products p ON p.id = sr.product_id
                WHERE sr.store_id  = :storeId
                  AND sr.session_id = :sessionId
                ORDER BY
                    CASE sr.status
                        WHEN 'critical' THEN 1
                        WHEN 'low'      THEN 2
                        WHEN 'ok'       THEN 3
                        ELSE                 4
                    END,
                    sr.variance ASC
                """)
                .param("storeId",   storeId)
                .param("sessionId", sessionId)
                .query(this::mapRow)
                .list();
    }

    private ZoneScanRollupRow mapRow(ResultSet rs, int n) throws SQLException {
        String rawId = rs.getString("id");
        String rawSession = rs.getString("session_id");
        return new ZoneScanRollupRow(
                rawId       != null ? UUID.fromString(rawId)       : null,
                UUID.fromString(rs.getString("store_id")),
                UUID.fromString(rs.getString("zone_id")),
                rs.getString("zone_name"),
                UUID.fromString(rs.getString("product_id")),
                rs.getString("sku"),
                rs.getString("product_name"),
                rawSession  != null ? UUID.fromString(rawSession)  : null,
                rs.getInt("scanned_qty"),
                rs.getInt("par_qty"),
                rs.getInt("min_qty"),
                rs.getInt("variance"),
                rs.getString("status"),
                rs.getString("computed_at")
        );
    }
}
