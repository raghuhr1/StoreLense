package com.storelense.store.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.store.dto.*;
import com.storelense.store.service.RfidReaderService;
import com.storelense.store.service.StoreFeatureService;
import com.storelense.store.service.StoreService;
import com.storelense.store.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
@Tag(name = "Stores", description = "Store and zone management")
public class StoreController {

    private final StoreService        storeService;
    private final ZoneService         zoneService;
    private final RfidReaderService   readerService;
    private final StoreFeatureService featureService;

    @GetMapping
    @Operation(summary = "List all active stores")
    public ResponseEntity<ApiResponse<PageResponse<StoreResponse>>> list(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.listStores(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get store by ID")
    public ResponseEntity<ApiResponse<StoreResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.getStore(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new store")
    public ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody CreateStoreRequest req) {
        StoreResponse store = storeService.createStore(req);
        featureService.seedDefaults(store.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Store created", store));
    }

    // --- Features ---

    @GetMapping("/{id}/features")
    @Operation(summary = "Get feature flags for a store")
    public ResponseEntity<ApiResponse<List<StoreFeatureResponse>>> getFeatures(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(featureService.getFeatures(id)));
    }

    @PutMapping("/{id}/features")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update feature flags for a store")
    public ResponseEntity<ApiResponse<List<StoreFeatureResponse>>> updateFeatures(
            @PathVariable UUID id, @RequestBody UpdateFeaturesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(featureService.updateFeatures(id, req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update store details")
    public ResponseEntity<ApiResponse<StoreResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateStoreRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(storeService.updateStore(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a store")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        storeService.deactivateStore(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- Zones ---

    @GetMapping("/{storeId}/zones")
    @Operation(summary = "List zones for a store")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> listZones(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(zoneService.listZones(storeId)));
    }

    @PostMapping("/{storeId}/zones")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Create a zone in a store")
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(
            @PathVariable UUID storeId, @Valid @RequestBody CreateZoneRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Zone created", zoneService.createZone(storeId, req)));
    }

    @PutMapping("/{storeId}/zones/{zoneId}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Update a zone")
    public ResponseEntity<ApiResponse<ZoneResponse>> updateZone(
            @PathVariable UUID storeId, @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateZoneRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(zoneService.updateZone(storeId, zoneId, req)));
    }

    // --- RFID Readers ---

    @GetMapping("/{storeId}/readers")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "List RFID readers for a store")
    public ResponseEntity<ApiResponse<List<RfidReaderResponse>>> listReaders(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(readerService.listReaders(storeId)));
    }

    @PostMapping("/{storeId}/readers")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Register a new RFID reader for a store")
    public ResponseEntity<ApiResponse<RfidReaderResponse>> createReader(
            @PathVariable UUID storeId, @Valid @RequestBody CreateReaderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reader registered", readerService.createReader(storeId, req)));
    }
}
