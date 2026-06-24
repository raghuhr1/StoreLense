package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.ZoneScanRollupRow;
import com.storelense.inventory.service.ZoneScanRollupService;
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
@RequestMapping("/api/scan-rollup")
@RequiredArgsConstructor
@Tag(name = "Scan Rollup", description = "Zone-level comparison of RFID scan counts vs par levels")
public class ZoneScanRollupController {

    private final ZoneScanRollupService service;

    @GetMapping("/live")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Live zone comparison — current epc_registry vs par levels",
               description = "Always reflects the latest scan state without requiring a completed session. " +
                             "Optionally filtered to a single zone.")
    public ResponseEntity<ApiResponse<List<ZoneScanRollupRow>>> getLive(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID zoneId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getLive(effective, zoneId)));
    }

    @PostMapping("/compute")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Compute and persist a scan-vs-par snapshot",
               description = "Runs the zone comparison for the store and saves the result tied to " +
                             "a SOH session ID. Replaces any prior snapshot for the same session. " +
                             "Call this when a SOH session is closed.")
    public ResponseEntity<ApiResponse<List<ZoneScanRollupRow>>> compute(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID sessionId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.computeAndSave(effective, sessionId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get persisted rollup for a SOH session",
               description = "Returns the snapshot computed when the session was closed.")
    public ResponseEntity<ApiResponse<List<ZoneScanRollupRow>>> getBySession(
            @RequestParam UUID storeId,
            @RequestParam UUID sessionId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getBySession(effective, sessionId)));
    }
}
