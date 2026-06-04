package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory state and EPC registry")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/state")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get current inventory state for a store")
    public ResponseEntity<ApiResponse<List<InventoryState>>> getStoreInventory(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStoreInventory(effective)));
    }

    @GetMapping("/low-accuracy")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get products with inventory accuracy below threshold")
    public ResponseEntity<ApiResponse<List<InventoryState>>> getLowAccuracy(
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "95.0") double threshold,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowAccuracyItems(effective, threshold)));
    }

    @GetMapping("/epc-summary")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "EPC count summary by status for a store")
    public ResponseEntity<ApiResponse<Map<String, Long>>> epcSummary(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "in_store",    inventoryService.countByStatus(effective, "in_store"),
                "missing",     inventoryService.countByStatus(effective, "missing"),
                "sold",        inventoryService.countByStatus(effective, "sold"),
                "damaged",     inventoryService.countByStatus(effective, "damaged")
        )));
    }
}
