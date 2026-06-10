package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.domain.entity.ExceptionEvent;
import com.storelense.inventory.service.ExceptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exceptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
@Tag(name = "Exceptions", description = "Inventory exception events (ghost tags, missing items, read misses)")
public class ExceptionController {

    private final ExceptionService exceptionService;

    @GetMapping("/summary")
    @Operation(summary = "Get actionable exception counts by type for a store")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSummary(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.getSummary(effective)));
    }

    @GetMapping
    @Operation(summary = "List exception events for a store, filtered by type")
    public ResponseEntity<ApiResponse<PageResponse<ExceptionEvent>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        var page = exceptionService.listByType(effective, type != null ? type.toUpperCase() : "GHOST_TAG", pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    // ── Ghost tag endpoints ────────────────────────────────────────────────

    @GetMapping("/ghost/{epc}")
    @Operation(summary = "Get the active ghost-tag exception for an EPC")
    public ResponseEntity<ApiResponse<ExceptionEvent>> getGhostDetail(
            @PathVariable String epc,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.getGhostDetail(epc, effective)));
    }

    @PostMapping("/ghost/{epc}/ignore")
    @Operation(summary = "Dismiss a ghost-tag exception as a known false positive")
    public ResponseEntity<ApiResponse<ExceptionEvent>> ignoreGhost(
            @PathVariable String epc,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.ignoreGhost(epc, effective)));
    }

    @PostMapping("/ghost/{epc}/investigate")
    @Operation(summary = "Mark a ghost-tag exception as under investigation")
    public ResponseEntity<ApiResponse<ExceptionEvent>> investigateGhost(
            @PathVariable String epc,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.investigateGhost(epc, effective)));
    }

    // ── Missing item endpoints ─────────────────────────────────────────────

    @GetMapping("/missing/{epc}")
    @Operation(summary = "Get the active missing-item exception for an EPC")
    public ResponseEntity<ApiResponse<ExceptionEvent>> getMissingDetail(
            @PathVariable String epc,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.getMissingDetail(epc, effective)));
    }

    @PostMapping("/missing/{epc}/mark-missing")
    @Operation(summary = "Confirm and resolve a missing-item exception")
    public ResponseEntity<ApiResponse<ExceptionEvent>> markMissing(
            @PathVariable String epc,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(exceptionService.markMissing(epc, effective)));
    }
}
