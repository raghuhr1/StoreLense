package com.storelense.erp.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.erp.domain.entity.CcReconciliation;
import com.storelense.erp.domain.entity.CcReconciliationItem;
import com.storelense.erp.domain.repository.CcReconciliationItemRepository;
import com.storelense.erp.service.ReconciliationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
@Tag(name = "Reconciliation", description = "Cycle-count ERP vs RFID reconciliation")
public class ReconciliationController {

    private final ReconciliationEngine           engine;
    private final CcReconciliationItemRepository itemRepository;

    // ── Session-scoped (existing) ──────────────────────────────────────────

    @GetMapping("/sessions")
    @Operation(summary = "List reconciliation runs for a store, newest first")
    public ResponseEntity<ApiResponse<PageResponse<CcReconciliation>>> listByStore(
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        var p = engine.listByStore(effective, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(p)));
    }

    @PostMapping("/sessions/{sessionId}/run")
    @Operation(summary = "Run ERP cycle-count reconciliation for a completed SOH session")
    public ResponseEntity<ApiResponse<CcReconciliation>> run(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(engine.reconcile(sessionId)));
    }

    @GetMapping("/sessions/{sessionId}/result")
    @Operation(summary = "Get the latest reconciliation result for a session")
    public ResponseEntity<ApiResponse<CcReconciliation>> getResult(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(engine.getLatestResult(sessionId)));
    }

    @GetMapping("/sessions/{sessionId}/result/items")
    @Operation(summary = "Get reconciliation line items as JSON for a session")
    public ResponseEntity<ApiResponse<List<CcReconciliationItem>>> getResultItems(@PathVariable UUID sessionId) {
        CcReconciliation recon = engine.getLatestResult(sessionId);
        return ResponseEntity.ok(ApiResponse.ok(engine.getItems(recon.getId())));
    }

    @GetMapping("/sessions/{sessionId}/result/csv")
    @Operation(summary = "Download reconciliation line items as CSV")
    public ResponseEntity<byte[]> getResultCsv(@PathVariable UUID sessionId) {
        CcReconciliation recon = engine.getLatestResult(sessionId);
        return buildCsvResponse(recon.getId(), "reconciliation-" + sessionId + ".csv");
    }

    // ── Cycle-count-scoped (new) ───────────────────────────────────────────

    @PostMapping("/cycle-counts/{id}/run")
    @Operation(summary = "Run reconciliation across all sessions belonging to a cycle count")
    public ResponseEntity<ApiResponse<CcReconciliation>> runByCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(engine.reconcileByCount(id)));
    }

    @GetMapping("/cycle-counts/{id}/result")
    @Operation(summary = "Get the latest reconciliation result for a cycle count")
    public ResponseEntity<ApiResponse<CcReconciliation>> getCountResult(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(engine.getLatestCountResult(id)));
    }

    @GetMapping("/cycle-counts/{id}/result/items")
    @Operation(summary = "Get reconciliation line items for a cycle count, optionally filtered by location")
    public ResponseEntity<ApiResponse<List<CcReconciliationItem>>> getCountResultItems(
            @PathVariable UUID id,
            @RequestParam(required = false) String location) {
        CcReconciliation recon = engine.getLatestCountResult(id);
        return ResponseEntity.ok(ApiResponse.ok(engine.getItemsByLocation(recon.getId(), location)));
    }

    @PostMapping("/cycle-counts/{id}/approve")
    @Operation(summary = "Approve a cycle count reconciliation result (PENDING_APPROVAL → APPROVED)")
    public ResponseEntity<ApiResponse<CcReconciliation>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        CcReconciliation recon = engine.getLatestCountResult(id);
        return ResponseEntity.ok(ApiResponse.ok(engine.approve(recon.getId(), principal.userId())));
    }

    @GetMapping("/cycle-counts/{id}/result/csv")
    @Operation(summary = "Download cycle count reconciliation as CSV including LOCATION and SECTION columns")
    public ResponseEntity<byte[]> getCountResultCsv(@PathVariable UUID id) {
        CcReconciliation recon = engine.getLatestCountResult(id);
        return buildCsvResponse(recon.getId(), "reconciliation-count-" + id + ".csv");
    }

    // ── Shared CSV builder ─────────────────────────────────────────────────

    private ResponseEntity<byte[]> buildCsvResponse(UUID reconciliationId, String filename) {
        List<CcReconciliationItem> items = itemRepository.findByReconciliation_Id(reconciliationId);

        StringBuilder csv = new StringBuilder("EPC,EAN,STATUS,EXPECTED_QTY,SCANNED_QTY,LOCATION,SECTION\n");
        for (CcReconciliationItem item : items) {
            csv.append(item.getEpc()).append(',')
               .append(item.getEan()          != null ? item.getEan()          : "").append(',')
               .append(item.getStatus()).append(',')
               .append(item.getExpectedQty()).append(',')
               .append(item.getScannedQty()).append(',')
               .append(item.getLocationCode() != null ? item.getLocationCode() : "").append(',')
               .append(item.getSectionCode()  != null ? item.getSectionCode()  : "").append('\n');
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
