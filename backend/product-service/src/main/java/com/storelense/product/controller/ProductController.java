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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product and EPC tag management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List or search products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.listProducts(search, pageable)));
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

    @GetMapping("/by-ean/{ean}/epcs")
    @Operation(summary = "Get all active EPC values for products matching an EAN barcode")
    public ResponseEntity<ApiResponse<List<String>>> getEpcsByEan(@PathVariable String ean) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getEpcsByEan(ean)));
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
