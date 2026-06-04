package com.storelense.erp.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.erp.domain.entity.ErpSyncLog;
import com.storelense.erp.domain.repository.ErpSyncLogRepository;
import com.storelense.erp.service.InventorySyncService;
import com.storelense.erp.service.ProductSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/erp/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ERP Admin", description = "Manual ERP sync triggers and audit")
public class ErpSyncAdminController {

    private final ProductSyncService   productSyncService;
    private final InventorySyncService inventorySyncService;
    private final ErpSyncLogRepository syncLogRepository;

    @PostMapping("/sync/products")
    @Operation(summary = "Manually trigger a full product master sync from ERP")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerProductSync() {
        ErpSyncLog log = productSyncService.runSync("manual");
        if (log == null) {
            return ResponseEntity.ok(ApiResponse.error("SYNC_RUNNING", "Product sync already in progress"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "syncId",   log.getId(),
                "status",   log.getStatus(),
                "fetched",  log.getRecordsFetched(),
                "published", log.getRecordsPublished(),
                "failed",   log.getRecordsFailed()
        )));
    }

    @PostMapping("/sync/inventory")
    @Operation(summary = "Manually trigger expected-inventory sync from ERP")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerInventorySync() {
        ErpSyncLog log = inventorySyncService.runSync("manual");
        if (log == null) {
            return ResponseEntity.ok(ApiResponse.error("SYNC_RUNNING", "Inventory sync already in progress"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "syncId",   log.getId(),
                "status",   log.getStatus(),
                "fetched",  log.getRecordsFetched(),
                "published", log.getRecordsPublished()
        )));
    }

    @GetMapping("/sync/logs")
    @Operation(summary = "List sync audit logs, optionally filtered by sync type")
    public ResponseEntity<ApiResponse<PageResponse<ErpSyncLog>>> listLogs(
            @RequestParam(required = false) String syncType,
            @PageableDefault(size = 20) Pageable pageable) {

        var page = syncType != null
                ? syncLogRepository.findBySyncTypeOrderByStartedAtDesc(syncType, pageable)
                : syncLogRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/sync/logs/latest")
    @Operation(summary = "Get the last successful sync record for each type")
    public ResponseEntity<ApiResponse<Map<String, Object>>> latestSyncs() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "productSync",   syncLogRepository.findLastSuccessful("PRODUCT_INBOUND").orElse(null),
                "inventorySync", syncLogRepository.findLastSuccessful("INVENTORY_INBOUND").orElse(null),
                "sohPush",       syncLogRepository.findLastSuccessful("SOH_OUTBOUND").orElse(null)
        )));
    }
}
