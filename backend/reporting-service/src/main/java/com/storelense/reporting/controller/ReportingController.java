package com.storelense.reporting.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.reporting.domain.entity.KpiDaily;
import com.storelense.reporting.domain.repository.KpiDailyRepository;
import com.storelense.reporting.dto.DashboardSummaryResponse;
import com.storelense.reporting.service.KpiAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "KPI dashboards and store performance reporting")
public class ReportingController {

    private final KpiDailyRepository     kpiDailyRepository;
    private final KpiAggregationService  aggregationService;

    @GetMapping("/kpi/daily")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get daily KPI history for a store")
    public ResponseEntity<ApiResponse<PageResponse<KpiDaily>>> getDailyKpi(
            @RequestParam UUID storeId,
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        var page = kpiDailyRepository.findByStoreIdOrderByKpiDateDesc(effective, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/kpi/range")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get KPI data for a store within a date range")
    public ResponseEntity<ApiResponse<List<KpiDaily>>> getKpiRange(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(
                kpiDailyRepository.findByStoreAndDateRange(effective, from, to)));
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get dashboard KPI summary with accuracy and exception trends")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary(
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(aggregationService.getDashboardSummary(effective, days)));
    }

    @PostMapping("/kpi/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger KPI aggregation for a specific store and date (admin)")
    public ResponseEntity<ApiResponse<Void>> triggerAggregation(
            @RequestParam UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        aggregationService.upsertKpiForStore(storeId, date);
        return ResponseEntity.ok(ApiResponse.ok("Aggregation triggered", null));
    }
}
