package com.storelense.product.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.product.dto.*;
import com.storelense.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product and EPC tag management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List or search products",
               description = "When storeId is supplied returns only products present in that store " +
                             "(via inventory_state or epc_registry). Omit storeId for the full global catalog (admin use). " +
                             "Pass sync=true for mobile offline-catalog download: bypasses the inventory filter " +
                             "so all active products are returned regardless of store inventory data. " +
                             "Pass since (epoch millis) for delta sync: only products updated after that timestamp.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "false") boolean sync,
            @RequestParam(required = false) Long since,
            @PageableDefault(size = 50) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        UUID effective = (principal != null && !principal.isAdmin())
                ? principal.storeId()
                : storeId;
        OffsetDateTime sinceDate = since != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(since), ZoneOffset.UTC)
                : null;
        // sync=true is mobile offline-catalog download; admins get the full global catalog,
        // store users always get only their own store's products regardless of sync flag
        boolean globalSync = sync && (principal == null || principal.isAdmin());
        return ResponseEntity.ok(ApiResponse.ok(
                globalSync ? productService.listAllActive(search, pageable)
                           : productService.listProducts(search, effective, sinceDate, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProduct(id)));
    }

    @GetMapping("/by-sku/{sku}")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductBySku(sku)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.createProduct(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateProduct(id, req)));
    }

    @GetMapping("/epc/{epc}")
    @Operation(summary = "Lookup product by EPC (Redis-cached)")
    public ResponseEntity<ApiResponse<EpcLookupResponse>> lookupEpc(@PathVariable String epc) {
        return ResponseEntity.ok(ApiResponse.ok(productService.lookupEpc(epc)));
    }

    @GetMapping("/by-ean/{ean}/exists")
    @Operation(summary = "Check if any product has a barcode with the given EAN value")
    public ResponseEntity<ApiResponse<Boolean>> existsByEan(@PathVariable String ean) {
        return ResponseEntity.ok(ApiResponse.ok(productService.existsByEan(ean)));
    }

    @GetMapping("/by-ean/{ean}/product")
    @Operation(summary = "Resolve a product by EAN/GTIN barcode value (used by RFID decode fallback)")
    public ResponseEntity<ApiResponse<EpcLookupResponse>> lookupByEan(@PathVariable String ean) {
        return ResponseEntity.ok(ApiResponse.ok(productService.lookupByEan(ean)));
    }

    @GetMapping("/by-ean/{ean}/epcs")
    @Operation(summary = "Get all active EPC values for products matching an EAN barcode")
    public ResponseEntity<ApiResponse<List<String>>> getEpcsByEan(@PathVariable String ean) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getEpcsByEan(ean)));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Upload/replace the product's image (JPEG, PNG or WEBP)")
    public ResponseEntity<ApiResponse<ProductResponse>> uploadImage(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded", productService.uploadImage(id, file)));
    }

    @GetMapping("/images/{filename}")
    @Operation(summary = "Serve a stored product image")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Resource resource = productService.loadImage(filename);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(filename).orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }

    @PostMapping("/images/bulk-import")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk-import product images from a CSV of gtin,image_url rows",
               description = "Matches each GTIN against products.barcodes and downloads the " +
                             "image server-side — runs as a background job since large CSVs " +
                             "take longer than an HTTP request timeout allows. Poll the " +
                             "returned jobId via GET /images/bulk-import/{jobId}.")
    public ResponseEntity<ApiResponse<UUID>> startBulkImageImport(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        return ResponseEntity.ok(ApiResponse.ok("Bulk image import started", productService.startBulkImageImport(file)));
    }

    @GetMapping("/images/bulk-import/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Poll a bulk image import job's progress")
    public ResponseEntity<ApiResponse<BulkImageImportStatusResponse>> getBulkImportStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getBulkImportStatus(jobId)));
    }

    @PostMapping("/{id}/epc")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Associate an EPC tag with a product")
    public ResponseEntity<ApiResponse<Void>> associateEpc(
            @PathVariable UUID id,
            @RequestParam String epc,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        productService.associateEpc(id, epc, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("EPC associated", null));
    }
}
