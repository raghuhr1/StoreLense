package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.dto.EpcLocationResponse;
import com.storelense.inventory.dto.EpcsByEanResponse;
import com.storelense.inventory.dto.MarkEpcsSoldRequest;
import com.storelense.inventory.dto.SkuLedgerRow;
import com.storelense.inventory.dto.SkuInventoryResponse;
import com.storelense.inventory.dto.UpsertExpectedQtyRequest;
import com.storelense.inventory.service.InventoryService;
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

    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get on-hand count and active EPC list for a SKU at a store")
    public ResponseEntity<ApiResponse<SkuInventoryResponse>> getSkuInventory(
            @PathVariable String sku,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getSkuInventory(sku, effective)));
    }

    @GetMapping("/epc/{epc}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Last-seen zone and timestamp for a single EPC",
               description = "Used by the handheld app after product search to show where an item " +
                             "was most recently scanned. Returns 404 if the EPC has never been seen at this store.")
    public ResponseEntity<ApiResponse<EpcLocationResponse>> getEpcLocation(
            @PathVariable String epc,
            @RequestParam(required = false) UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getEpcLocation(epc, effective)));
    }

    @GetMapping("/epc-by-ean/{ean}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','SECURITY_GUARD')")
    @Operation(summary = "Resolve EAN barcode → in-store EPCs",
               description = "Used by C66 gate app: given an EAN from the customer bill, " +
                             "returns the product name and every EPC currently in_store at that store.")
    public ResponseEntity<ApiResponse<EpcsByEanResponse>> getEpcsByEan(
            @PathVariable String ean,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getEpcsByEan(ean, effective)));
    }

    @GetMapping("/sku-ledger")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Per-SKU RFID ledger — EPC counts by status (in_store / sold / missing / damaged)")
    public ResponseEntity<ApiResponse<List<SkuLedgerRow>>> getSkuLedger(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getSkuLedger(effective)));
    }

    @PostMapping("/epc/sold")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','SECURITY_GUARD')")
    @Operation(summary = "Mark EPCs as sold",
               description = "Called by the C66 security gate app after matching bill QR EPCs with RFID bag scan. " +
                             "Transitions matching EPCs from 'in_store' to 'sold' and decrements on-hand counts.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markEpcsSold(
            @Valid @RequestBody MarkEpcsSoldRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID storeId = principal.isAdmin() ? req.storeId() : principal.storeId();
        int marked = inventoryService.markEpcsSold(storeId, req.epcs());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "marked",   marked,
                "total",    req.epcs().size(),
                "notFound", req.epcs().size() - marked
        )));
    }

    @PostMapping("/expected")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Upsert ERP expected quantity for a store/product/zone",
               description = "Sets quantity_expected used to calculate inventory accuracy vs RFID scan data. " +
                             "Called by the XLS upload tool. Preserves existing quantity_on_hand from RFID scans.")
    public ResponseEntity<ApiResponse<InventoryState>> upsertExpected(
            @Valid @RequestBody UpsertExpectedQtyRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID storeId = principal.isAdmin() ? req.storeId() : principal.storeId();
        InventoryState result = inventoryService.upsertExpectedQty(
                storeId, req.productId(), req.zoneId(), req.quantityExpected());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
