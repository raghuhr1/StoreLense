package com.storelense.inventory.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.ReplenishmentRule;
import com.storelense.inventory.domain.repository.ReplenishmentRuleRepository;
import com.storelense.inventory.dto.ReplenishmentRuleRequest;
import com.storelense.inventory.dto.ReplenishmentRuleResponse;
import com.storelense.inventory.dto.ReplenishmentSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReplenishmentRuleService {

    private final ReplenishmentRuleRepository repository;
    private final JdbcClient jdbcClient;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReplenishmentRuleResponse> list(UUID storeId) {
        return repository.findByStoreIdAndActiveTrue(storeId)
                .stream().map(ReplenishmentRuleResponse::from).toList();
    }

    @SuppressWarnings("null")
    @Transactional
    public ReplenishmentRuleResponse upsert(UUID storeId, ReplenishmentRuleRequest req) {
        ReplenishmentRule entity = repository
                .findByStoreIdAndTriggerStatus(storeId, req.triggerStatus())
                .orElseGet(() -> ReplenishmentRule.builder()
                        .storeId(storeId)
                        .triggerStatus(req.triggerStatus())
                        .build());
        entity.setPriority(req.priority());
        entity.setActive(true);
        return ReplenishmentRuleResponse.from(repository.save(entity));
    }

    @SuppressWarnings("null")
    @Transactional
    public void delete(UUID id, UUID storeId) {
        ReplenishmentRule entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentRule", id));
        if (!entity.getStoreId().equals(storeId))
            throw new ResourceNotFoundException("ReplenishmentRule", id);
        entity.setActive(false);
        repository.save(entity);
    }

    // ── SUGGESTIONS ───────────────────────────────────────────────────────────

    /**
     * Phase 3 cutover: sources from the store's most recent completed SOH session's Sales
     * Floor count (soh.soh_session_items) instead of live antenna/zone scans
     * (inventory.epc_registry). Joined against store_location_par_levels +
     * replenishment_rules, same dedup-against-open-tasks logic as before.
     * trigger_status='low'      matches rollup rows with status 'low' OR 'critical'.
     * trigger_status='critical' matches only 'critical' rows.
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestion> getSuggestions(UUID storeId) {
        return jdbcClient.sql("""
                WITH latest_session AS (
                    SELECT id FROM soh.soh_sessions
                    WHERE store_id = :storeId AND status = 'completed'
                    ORDER BY completed_at DESC
                    LIMIT 1
                ),
                scan AS (
                    SELECT i.product_id, SUM(i.counted_quantity) AS cnt
                    FROM soh.soh_session_items i
                    WHERE i.session_id = (SELECT id FROM latest_session)
                      AND i.location_code = 'SALES_FLOOR'
                    GROUP BY i.product_id
                ),
                rollup AS (
                    SELECT
                        pl.store_id,
                        pl.location_code,
                        pl.product_id,
                        p.sku,
                        p.name                          AS product_name,
                        COALESCE(s.cnt, 0)              AS scanned_qty,
                        pl.par_qty,
                        pl.min_qty,
                        COALESCE(s.cnt, 0) - pl.par_qty AS variance,
                        CASE
                            WHEN COALESCE(s.cnt, 0) <= pl.min_qty THEN 'critical'
                            WHEN COALESCE(s.cnt, 0) <  pl.par_qty THEN 'low'
                        END AS rollup_status
                    FROM inventory.store_location_par_levels pl
                    LEFT JOIN products.products p ON p.id = pl.product_id
                    LEFT JOIN scan s ON s.product_id = pl.product_id
                    WHERE pl.store_id = :storeId AND pl.active = true
                      AND pl.location_code = 'SALES_FLOOR'
                )
                SELECT
                    r.store_id,
                    r.location_code,
                    r.product_id,
                    r.sku,
                    r.product_name,
                    r.scanned_qty,
                    r.par_qty,
                    ABS(r.variance)            AS shortage,
                    r.rollup_status            AS status,
                    rl.priority,
                    EXISTS (
                        SELECT 1
                        FROM refill.refill_tasks  t
                        JOIN refill.refill_task_items i ON i.task_id = t.id
                        WHERE t.store_id    = :storeId
                          AND i.product_id  = r.product_id
                          AND t.status NOT IN ('completed', 'cancelled')
                    )                          AS has_open_task
                FROM rollup r
                JOIN inventory.replenishment_rules rl
                    ON rl.store_id = :storeId AND rl.active = true
                   AND (
                           (rl.trigger_status = 'low'      AND r.rollup_status IN ('low', 'critical'))
                        OR (rl.trigger_status = 'critical' AND r.rollup_status = 'critical')
                   )
                WHERE r.rollup_status IS NOT NULL
                ORDER BY
                    CASE r.rollup_status WHEN 'critical' THEN 1 ELSE 2 END,
                    r.variance ASC
                """)
                .param("storeId", storeId)
                .query(this::mapSuggestion)
                .list();
    }

    private ReplenishmentSuggestion mapSuggestion(ResultSet rs, int n) throws SQLException {
        return new ReplenishmentSuggestion(
                UUID.fromString(rs.getString("store_id")),
                rs.getString("location_code"),
                UUID.fromString(rs.getString("product_id")),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getInt("scanned_qty"),
                rs.getInt("par_qty"),
                rs.getInt("shortage"),
                rs.getString("status"),
                rs.getShort("priority"),
                rs.getBoolean("has_open_task")
        );
    }
}
