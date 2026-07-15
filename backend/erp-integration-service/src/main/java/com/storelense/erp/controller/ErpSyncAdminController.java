package com.storelense.erp.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.erp.domain.entity.ErpSyncLog;
import com.storelense.erp.domain.repository.ErpImportBatchRepository;
import com.storelense.erp.domain.repository.ErpSohSnapshotRepository;
import com.storelense.erp.domain.repository.ErpSyncLogRepository;
import com.storelense.erp.exception.CsvParseException;
import com.storelense.erp.service.EanResolutionService;
import com.storelense.erp.service.ErpImportService;
import com.storelense.erp.service.InventorySyncService;
import com.storelense.erp.service.ProductSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/erp/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ERP Admin", description = "Manual ERP sync triggers and audit")
public class ErpSyncAdminController {

    private final ProductSyncService       productSyncService;
    private final InventorySyncService     inventorySyncService;
    private final ErpSyncLogRepository     syncLogRepository;
    private final ErpImportService         importService;
    private final EanResolutionService     eanResolutionService;
    private final ErpImportBatchRepository importBatchRepository;
    private final ErpSohSnapshotRepository snapshotRepository;

    // ── Existing sync endpoints ────────────────────────────────────────────

    @PostMapping("/sync/products")
    @Operation(summary = "Manually trigger a full product master sync from ERP")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerProductSync() {
        ErpSyncLog log = productSyncService.runSync("manual");
        if (log == null) {
            return ResponseEntity.ok(ApiResponse.error("SYNC_RUNNING", "Product sync already in progress"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "syncId",    log.getId(),
                "status",    log.getStatus(),
                "fetched",   log.getRecordsFetched(),
                "published", log.getRecordsPublished(),
                "failed",    log.getRecordsFailed()
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
                "syncId",    log.getId(),
                "status",    log.getStatus(),
                "fetched",   log.getRecordsFetched(),
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

    // ── ERP import endpoints ───────────────────────────────────────────────

    @PostMapping(value = "/import/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an ERP SOH CSV file directly and trigger import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadImport(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("EMPTY_FILE", "Uploaded file is empty"));
        }

        Path temp = Objects.requireNonNull(Files.createTempFile("erp-import-", ".csv"));
        try {
            file.transferTo(temp);
            var batch = importService.processFile(temp);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "batchId",        batch.getId(),
                    "status",         batch.getStatus(),
                    "totalRows",      batch.getTotalRows(),
                    "unresolvedRows", batch.getUnresolvedRows()
            )));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    record ImportTriggerRequest(@NotBlank String path, String type) {
        String resolvedType() { return type != null ? type.toUpperCase() : "FILE"; }
    }

    @PostMapping("/import")
    @Operation(summary = "Manually trigger an ERP SOH import from a local path or S3 key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerImport(
            @Valid @RequestBody ImportTriggerRequest request) {

        var batch = "S3".equals(request.resolvedType())
                ? importService.processFile(request.path())
                : importService.processFile(Path.of(request.path()));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "batchId",        batch.getId(),
                "status",         batch.getStatus(),
                "totalRows",      batch.getTotalRows(),
                "unresolvedRows", batch.getUnresolvedRows()
        )));
    }

    @GetMapping("/import/batches")
    @Operation(summary = "List ERP import batches (paginated, newest first)")
    public ResponseEntity<ApiResponse<PageResponse<?>>> listBatches(
            @RequestParam(required = false) UUID storeId,
            @PageableDefault(size = 20) Pageable pageable) {

        var page = storeId != null
                ? importBatchRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable)
                : importBatchRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/import/batches/{batchId}")
    @Operation(summary = "Get ERP import batch detail with unresolved snapshot count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBatch(@PathVariable UUID batchId) {
        return importBatchRepository.findById(batchId)
                .map(batch -> {
                    long unresolved = snapshotRepository.countUnresolvedByBatchId(batchId);
                    return ResponseEntity.ok(ApiResponse.ok(Map.of(
                            "batch",           batch,
                            "unresolvedCount", unresolved
                    )));
                })
                .orElseGet(() -> ResponseEntity.ok(
                        ApiResponse.error("NOT_FOUND", "Batch " + batchId + " not found")));
    }

    @GetMapping("/import/batches/{batchId}/unresolved")
    @Operation(summary = "List snapshots that could not be resolved to EPCs for a given batch")
    public ResponseEntity<ApiResponse<List<?>>> getUnresolvedSnapshots(@PathVariable UUID batchId) {
        var rows = snapshotRepository.findNotResolvedByBatchId(batchId);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping("/import/batches/{batchId}/re-resolve")
    @Operation(summary = "Retry EAN→EPC resolution for all unresolved snapshots in a batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reResolve(@PathVariable UUID batchId) {
        if (!importBatchRepository.existsById(batchId)) {
            return ResponseEntity.ok(ApiResponse.error("NOT_FOUND", "Batch " + batchId + " not found"));
        }
        eanResolutionService.resolveAll(batchId);
        long unresolved = snapshotRepository.countUnresolvedByBatchId(batchId);
        long resolved   = snapshotRepository.countByBatch_IdAndResolutionStatus(batchId, "RESOLVED");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "batchId",    batchId,
                "resolved",   resolved,
                "unresolved", unresolved
        )));
    }

    @ExceptionHandler(CsvParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleCsvParse(CsvParseException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("CSV_PARSE_ERROR", ex.getMessage()));
    }
}
