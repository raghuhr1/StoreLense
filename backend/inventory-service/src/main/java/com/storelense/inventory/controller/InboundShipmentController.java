package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.*;
import com.storelense.inventory.service.InboundShipmentService;
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
@RequestMapping("/api/inbound/shipments")
@RequiredArgsConstructor
@Tag(name = "Inbound Shipments", description = "DC → Store receiving workflow")
public class InboundShipmentController {

    private final InboundShipmentService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "List inbound shipments for a store")
    public ResponseEntity<ApiResponse<PageResponse<InboundShipmentResponse>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.listShipments(effective, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Get a single inbound shipment")
    public ResponseEntity<ApiResponse<InboundShipmentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getShipment(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Create a new inbound shipment (from web portal)")
    public ResponseEntity<ApiResponse<InboundShipmentResponse>> create(
            @Valid @RequestBody CreateShipmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Shipment created", service.createShipment(req)));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Mark a shipment as received with scanned EPCs")
    public ResponseEntity<ApiResponse<ReceiveShipmentResponse>> receive(
            @PathVariable UUID id,
            @RequestBody ReceiveShipmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Shipment received", service.receiveShipment(id, req)));
    }
}
