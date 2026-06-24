package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.ProductFrequencyRow;
import com.storelense.inventory.dto.ZoneHealthSummary;
import com.storelense.inventory.dto.ZoneTrendPoint;
import com.storelense.inventory.service.ZoneIntelligenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/zone-intelligence")
@RequiredArgsConstructor
@Tag(name = "Zone Intelligence", description = "Aggregated zone health, product frequency, and trend analytics")
public class ZoneIntelligenceController {

    private final ZoneIntelligenceService service;

    @GetMapping("/zone-health")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Live zone health — product counts by status per zone",
               description = "Computes critical/low/ok/surplus product counts for every zone " +
                             "that has par levels configured. Always reflects current scan state.")
    public ResponseEntity<ApiResponse<List<ZoneHealthSummary>>> getZoneHealth(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getZoneHealth(effective)));
    }

    @GetMapping("/product-frequency")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Top products by auto-triggered refill frequency",
               description = "Products that frequently appear in soh_trigger refill tasks. " +
                             "Useful for identifying chronic under-stockers that need higher par levels.")
    public ResponseEntity<ApiResponse<List<ProductFrequencyRow>>> getProductFrequency(
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getProductFrequency(effective, days, limit)));
    }

    @GetMapping("/zone-trend")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Daily zone health trend from persisted scan snapshots",
               description = "Each data point is one calendar day of zone_scan_rollup data. " +
                             "Optionally scoped to a single zone for drill-down.")
    public ResponseEntity<ApiResponse<List<ZoneTrendPoint>>> getZoneTrend(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getZoneTrend(effective, zoneId, days)));
    }
}
