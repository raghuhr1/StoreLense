package com.storelense.refill.service;

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
