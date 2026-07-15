package com.storelense.refill.service;

import com.storelense.common.event.EpcSoldEvent;
import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.refill.dto.CreateRefillTaskRequest;
import com.storelense.refill.dto.TaskItemRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 of the SOH -> Sales Floor/Backroom replenishment model.
 * Replaces the live, antenna-Zone-based replenishment trigger: instead of reacting to
 * continuous RFID reads, this reacts to a completed SOH session and compares the
 * Sales-Floor count it just produced against configured store_location_par_levels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SohAutoReplenishmentService {

    /** Placeholder "system" actor for auto-created tasks — no human user initiates these. */
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final JdbcClient jdbcClient;
    private final RefillTaskService refillTaskService;

    @Transactional
    public void onSohSessionCompleted(SohSessionCompletedEvent event) {
        List<Shortage> shortages = findShortages(event.sessionId(), event.storeId());
        if (shortages.isEmpty()) {
            return;
        }

        for (Shortage s : shortages) {
            if (hasOpenTaskForSession(event.sessionId(), s.productId())) {
                continue;
            }
            refillTaskService.createTask(
                    new CreateRefillTaskRequest(
                            event.storeId(),
                            "replenishment",
                            "critical".equals(s.status()) ? 3 : 6,
                            "soh_auto",
                            event.sessionId(),
                            null,
                            "Auto-created from SOH session: Sales Floor %d/%d (par %d)"
                                    .formatted(s.floorCounted(), s.parQty(), s.parQty()),
                            List.of(new TaskItemRequest(s.productId(), null, s.shortage()))
                    ),
                    SYSTEM_USER_ID
            );
            log.info("Auto-created refill task from SOH session {} for product {} (shortage {})",
                    event.sessionId(), s.productId(), s.shortage());
        }
    }

    /**
     * Live trigger: reacts to a sale (gate-exit confirmed) without waiting for the next SOH
     * session. Estimates current Sales Floor qty as (last completed session's Sales Floor
     * count for this product) minus (units of this product sold since that session completed),
     * and compares against store_location_par_levels — same rule-matching and dedup as the
     * session-completion trigger, just keyed on product instead of a specific session.
     */
    @Transactional
    public void onItemSold(EpcSoldEvent event) {
        findLiveShortage(event.storeId(), event.productId()).ifPresent(s -> {
            if (hasOpenTaskForProduct(event.storeId(), s.productId())) {
                return;
            }
            refillTaskService.createTask(
                    new CreateRefillTaskRequest(
                            event.storeId(),
                            "replenishment",
                            "critical".equals(s.status()) ? 3 : 6,
                            "soh_auto_live",
                            null,
                            null,
                            "Auto-created from a sale: live Sales Floor estimate %d/%d (par %d)"
                                    .formatted(s.floorCounted(), s.parQty(), s.parQty()),
                            List.of(new TaskItemRequest(s.productId(), null, s.shortage()))
                    ),
                    SYSTEM_USER_ID
            );
            log.info("Auto-created refill task from sale of product {} at store {} (live shortage {})",
                    s.productId(), event.storeId(), s.shortage());
        });
    }

    /**
     * Cross-schema query: live Sales Floor estimate = last completed SOH session's Sales
     * Floor count for this product, minus units sold since that session completed.
     */
    private Optional<Shortage> findLiveShortage(UUID storeId, UUID productId) {
        return jdbcClient.sql("""
                WITH latest_session AS (
                    SELECT id, completed_at FROM soh.soh_sessions
                    WHERE store_id = :storeId AND status = 'completed'
                    ORDER BY completed_at DESC
                    LIMIT 1
                ),
                floor_count AS (
                    SELECT COALESCE(SUM(i.counted_quantity), 0) AS cnt
                    FROM soh.soh_session_items i
                    WHERE i.session_id = (SELECT id FROM latest_session)
                      AND i.location_code = 'SALES_FLOOR'
                      AND i.product_id = :productId
                ),
                sold_since AS (
                    SELECT COUNT(*) AS cnt
                    FROM inventory.epc_registry er
                    WHERE er.store_id = :storeId
                      AND er.product_id = :productId
                      AND er.status = 'sold'
                      AND er.last_seen_at > (SELECT completed_at FROM latest_session)
                ),
                live AS (
                    SELECT
                        GREATEST(0, (SELECT cnt FROM floor_count) - (SELECT cnt FROM sold_since)) AS live_floor_qty,
                        pl.par_qty,
                        pl.min_qty,
                        pl.par_qty - GREATEST(0, (SELECT cnt FROM floor_count) - (SELECT cnt FROM sold_since)) AS shortage,
                        CASE
                            WHEN GREATEST(0, (SELECT cnt FROM floor_count) - (SELECT cnt FROM sold_since)) <= pl.min_qty THEN 'critical'
                            ELSE 'low'
                        END AS status
                    FROM inventory.store_location_par_levels pl
                    WHERE pl.store_id = :storeId AND pl.product_id = :productId
                      AND pl.location_code = 'SALES_FLOOR' AND pl.active = true
                      AND pl.par_qty > GREATEST(0, (SELECT cnt FROM floor_count) - (SELECT cnt FROM sold_since))
                )
                SELECT l.live_floor_qty, l.par_qty, l.min_qty, l.shortage, l.status
                FROM live l
                JOIN inventory.replenishment_rules rl
                    ON rl.store_id = :storeId AND rl.active = true
                   AND (
                           (rl.trigger_status = 'low'      AND l.status IN ('low', 'critical'))
                        OR (rl.trigger_status = 'critical' AND l.status = 'critical')
                   )
                """)
                .param("storeId", storeId)
                .param("productId", productId)
                .query((rs, n) -> new Shortage(
                        productId,
                        rs.getInt("live_floor_qty"),
                        rs.getInt("par_qty"),
                        rs.getInt("min_qty"),
                        rs.getInt("shortage"),
                        rs.getString("status")
                ))
                .optional();
    }

    private boolean hasOpenTaskForProduct(UUID storeId, UUID productId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM refill.refill_tasks t
                JOIN refill.refill_task_items i ON i.task_id = t.id
                WHERE t.store_id = :storeId
                  AND i.product_id = :productId
                  AND t.status NOT IN ('completed', 'cancelled')
                """)
                .param("storeId", storeId)
                .param("productId", productId)
                .query(Integer.class).optional().orElse(0);
        return count != null && count > 0;
    }

    /**
     * Cross-schema query: joins this session's soh.soh_session_items (Sales Floor bucket only)
     * against inventory.store_location_par_levels to find products below par, then filters
     * through inventory.replenishment_rules — the same low/critical + priority config the
     * admin sets on the Store Detail page, previously only consumed by the Zone-based
     * suggestion query in ReplenishmentRuleService.
     */
    private List<Shortage> findShortages(UUID sessionId, UUID storeId) {
        return jdbcClient.sql("""
                WITH rollup AS (
                    SELECT
                        i.product_id                          AS product_id,
                        SUM(i.counted_quantity)                AS floor_counted,
                        pl.par_qty                             AS par_qty,
                        pl.min_qty                              AS min_qty,
                        pl.par_qty - SUM(i.counted_quantity)   AS shortage,
                        CASE
                            WHEN SUM(i.counted_quantity) <= pl.min_qty THEN 'critical'
                            ELSE 'low'
                        END AS status
                    FROM soh.soh_session_items i
                    JOIN inventory.store_location_par_levels pl
                         ON pl.store_id = :storeId
                        AND pl.product_id = i.product_id
                        AND pl.location_code = 'SALES_FLOOR'
                        AND pl.active = true
                    WHERE i.session_id = :sessionId
                      AND i.location_code = 'SALES_FLOOR'
                    GROUP BY i.product_id, pl.par_qty, pl.min_qty
                    HAVING pl.par_qty - SUM(i.counted_quantity) > 0
                )
                SELECT r.product_id, r.floor_counted, r.par_qty, r.min_qty, r.shortage, r.status
                FROM rollup r
                JOIN inventory.replenishment_rules rl
                    ON rl.store_id = :storeId AND rl.active = true
                   AND (
                           (rl.trigger_status = 'low'      AND r.status IN ('low', 'critical'))
                        OR (rl.trigger_status = 'critical' AND r.status = 'critical')
                   )
                """)
                .param("sessionId", sessionId)
                .param("storeId", storeId)
                .query(this::mapShortage)
                .list();
    }

    private boolean hasOpenTaskForSession(UUID sessionId, UUID productId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM refill.refill_tasks t
                JOIN refill.refill_task_items i ON i.task_id = t.id
                WHERE t.source_session_id = :sessionId
                  AND i.product_id = :productId
                  AND t.status NOT IN ('completed', 'cancelled')
                """)
                .param("sessionId", sessionId)
                .param("productId", productId)
                .query(Integer.class).optional().orElse(0);
        return count != null && count > 0;
    }

    private Shortage mapShortage(ResultSet rs, int n) throws SQLException {
        return new Shortage(
                UUID.fromString(rs.getString("product_id")),
                rs.getInt("floor_counted"),
                rs.getInt("par_qty"),
                rs.getInt("min_qty"),
                rs.getInt("shortage"),
                rs.getString("status")
        );
    }

    private record Shortage(UUID productId, int floorCounted, int parQty, int minQty, int shortage, String status) {}
}
