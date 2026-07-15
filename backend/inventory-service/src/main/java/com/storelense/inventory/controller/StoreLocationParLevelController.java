package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.StoreLocationParLevelRequest;
import com.storelense.inventory.dto.StoreLocationParLevelResponse;
import com.storelense.inventory.service.StoreLocationParLevelService;
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
@RequestMapping("/api/store-location-par-levels")
@RequiredArgsConstructor
@Tag(name = "Store Location Par Levels", description = "Target Sales Floor quantities per product per store")
public class StoreLocationParLevelController {

    private final StoreLocationParLevelService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "List store-location par levels for a store, optionally filtered by location")
    public ResponseEntity<ApiResponse<List<StoreLocationParLevelResponse>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String locationCode,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.list(effective, locationCode)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create or update a store-location par level (upsert by store+location+product)")
    public ResponseEntity<ApiResponse<StoreLocationParLevelResponse>> upsert(
            @Valid @RequestBody StoreLocationParLevelRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID storeId = principal.isAdmin() ? req.storeId() : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.upsert(storeId, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a store-location par level (sets active=false)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        service.delete(id, effective);
        return ResponseEntity.noContent().build();
    }
}
