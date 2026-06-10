package com.storelense.reporting.service;

import com.storelense.reporting.domain.entity.KpiDaily;
import com.storelense.reporting.domain.repository.KpiDailyRepository;
import com.storelense.reporting.dto.DashboardSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiAggregationService {

    private final JdbcClient          jdbcClient;
    private final KpiDailyRepository  kpiDailyRepository;

    // Runs nightly at 02:00 server time
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runNightlyAggregation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting nightly KPI aggregation for {}", yesterday);

        List<UUID> storeIds = fetchAllActiveStoreIds();
        int count = 0;
        for (UUID storeId : storeIds) {
            try {
                upsertKpiForStore(storeId, yesterday);
                count++;
            } catch (Exception e) {
                log.error("KPI aggregation failed for store {}: {}", storeId, e.getMessage());
            }
        }

        refreshMaterializedViews();
        log.info("Nightly KPI aggregation complete: {} stores processed for {}", count, yesterday);
    }

    public void upsertKpiForStore(UUID storeId, LocalDate date) {
        jdbcClient.sql("SELECT reporting.fn_upsert_kpi_daily(:storeId::uuid, :date::date)")
                .param("storeId", storeId.toString())
                .param("date", date.toString())
                .query();
    }

    public void refreshMaterializedViews() {
        try {
            jdbcClient.sql("SELECT reporting.fn_refresh_all_materialized_views()").query();
            log.info("Materialized views refreshed");
        } catch (Exception e) {
            log.error("Materialized view refresh failed: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(UUID storeId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(days - 1);

        // ── Accuracy history from kpi_daily ──────────────────────────────────
        List<KpiDaily> kpiRows = kpiDailyRepository.findByStoreAndDateRange(storeId, from, today);

        Map<LocalDate, Float> accuracyByDate = kpiRows.stream()
                .filter(k -> k.getInventoryAccuracyPct() != null)
                .collect(Collectors.toMap(KpiDaily::getKpiDate,
                        k -> k.getInventoryAccuracyPct().floatValue()));

        List<Float> accuracyHistory = buildHistory(from, days, d -> accuracyByDate.getOrDefault(d, null));

        // Most-recent non-null day is the headline figure
        Float sohAccuracy = kpiRows.stream()
                .filter(k -> k.getInventoryAccuracyPct() != null)
                .reduce((a, b) -> b)
                .map(k -> k.getInventoryAccuracyPct().floatValue())
                .orElse(null);

        // ── Exception counts from inventory schema ────────────────────────────
        List<Integer> missingHistory  = fetchExceptionHistory(storeId, "MISSING_EPC", from, today, days);
        List<Integer> ghostHistory    = fetchExceptionHistory(storeId, "GHOST_TAG",   from, today, days);
        List<Integer> readMissHistory = fetchExceptionHistory(storeId, "READ_MISS",   from, today, days);

        return new DashboardSummaryResponse(
                sohAccuracy,       accuracyHistory,
                fetchOpenCount(storeId, "MISSING_EPC"), missingHistory,
                fetchOpenCount(storeId, "GHOST_TAG"),   ghostHistory,
                fetchOpenCount(storeId, "READ_MISS"),   readMissHistory
        );
    }

    private List<Integer> fetchExceptionHistory(UUID storeId, String type,
                                                LocalDate from, LocalDate today, int days) {
        Map<LocalDate, Integer> byDate = jdbcClient.sql("""
                SELECT DATE(created_at AT TIME ZONE 'UTC') AS day, COUNT(*)::int AS cnt
                FROM inventory.exception_events
                WHERE store_id = :storeId::uuid
                  AND type = :type
                  AND DATE(created_at AT TIME ZONE 'UTC') BETWEEN :from::date AND :today::date
                GROUP BY day
                ORDER BY day
                """)
                .param("storeId", storeId.toString())
                .param("type", type)
                .param("from", from.toString())
                .param("today", today.toString())
                .query((rs, rowNum) -> Map.entry(
                        rs.getObject("day", LocalDate.class),
                        rs.getInt("cnt")
                ))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return buildHistory(from, days, d -> byDate.getOrDefault(d, 0));
    }

    private int fetchOpenCount(UUID storeId, String type) {
        return Objects.requireNonNullElse(
                jdbcClient.sql("""
                        SELECT COUNT(*)::int FROM inventory.exception_events
                        WHERE store_id = :storeId::uuid
                          AND type = :type
                          AND status IN ('OPEN', 'INVESTIGATING')
                        """)
                        .param("storeId", storeId.toString())
                        .param("type", type)
                        .query(Integer.class)
                        .single(),
                0);
    }

    private <T> List<T> buildHistory(LocalDate from, int days, java.util.function.Function<LocalDate, T> mapper) {
        return IntStream.range(0, days)
                .mapToObj(i -> mapper.apply(from.plusDays(i)))
                .collect(Collectors.toList());
    }

    private List<UUID> fetchAllActiveStoreIds() {
        return jdbcClient.sql("SELECT id FROM stores.stores WHERE is_active = true")
                .query(UUID.class).list();
    }
}
