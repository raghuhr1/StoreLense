package com.storelense.reporting.service;

import com.storelense.reporting.domain.repository.KpiDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    private List<UUID> fetchAllActiveStoreIds() {
        return jdbcClient.sql("SELECT id FROM stores.stores WHERE is_active = true")
                .query(UUID.class).list();
    }
}
