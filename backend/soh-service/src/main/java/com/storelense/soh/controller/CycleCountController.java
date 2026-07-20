package com.storelense.soh.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.soh.dto.CreateCycleCountRequest;
import com.storelense.soh.dto.CycleCountResponse;
import com.storelense.soh.service.CycleCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cycle-counts")
@RequiredArgsConstructor
@Tag(name = "Cycle Counts", description = "Cycle count event management — groups Floor + Backroom sessions under one count ID")
public class CycleCountController {

    private final CycleCountService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "List cycle counts for a store, newest first")
    public ResponseEntity<ApiResponse<PageResponse<CycleCountResponse>>> list(
            @RequestParam UUID storeId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.list(effective, pageable)));
    }

    // No @PreAuthorize: erp-integration-service calls this internally (unauthenticated,
    // permitAll in SecurityConfig) to fetch a cycle count's child sessions for combined
    // reconciliation — same pattern as SohSessionController.get().
    @GetMapping("/{id}")
    @Operation(summary = "Get cycle count detail with all child sessions")
    public ResponseEntity<ApiResponse<CycleCountResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Create a new cycle count (DRAFT status)")
    public ResponseEntity<ApiResponse<CycleCountResponse>> create(
            @Valid @RequestBody CreateCycleCountRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cycle count created",
                        service.create(req, principal.userId())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Advance cycle count from DRAFT to RUNNING")
    public ResponseEntity<ApiResponse<CycleCountResponse>> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(service.transition(id, "RUNNING", principal.userId())));
    }

    @PostMapping("/{id}/upload")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Mark cycle count as UPLOADED after ERP push")
    public ResponseEntity<ApiResponse<CycleCountResponse>> upload(
            @PathVariable UUID id,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(service.transition(id, "UPLOADED", principal.userId())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Close a cycle count (terminal state)")
    public ResponseEntity<ApiResponse<CycleCountResponse>> close(
            @PathVariable UUID id,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(service.transition(id, "CLOSED", principal.userId())));
    }
}
