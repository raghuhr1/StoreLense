package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.ZoneParLevelRequest;
import com.storelense.inventory.dto.ZoneParLevelResponse;
import com.storelense.inventory.service.ZoneParLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/par-levels")
@RequiredArgsConstructor
@Tag(name = "Zone Par Levels", description = "Target floor quantities per product per zone")
public class ZoneParLevelController {

    private final ZoneParLevelService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "List zone par levels for a store, optionally filtered by zone")
    public ResponseEntity<ApiResponse<List<ZoneParLevelResponse>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID zoneId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.list(effective, zoneId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create or update a zone par level (upsert by store+zone+product)")
    public ResponseEntity<ApiResponse<ZoneParLevelResponse>> upsert(
            @Valid @RequestBody ZoneParLevelRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID storeId = principal.isAdmin() ? req.storeId() : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.upsert(storeId, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a zone par level (sets active=false)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        service.delete(id, effective);
        return ResponseEntity.noContent().build();
    }
}
