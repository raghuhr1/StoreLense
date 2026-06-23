package com.storelense.soh.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.soh.dto.CreateTransferRequest;
import com.storelense.soh.dto.ReceiveTransferRequest;
import com.storelense.soh.dto.TransferResponse;
import com.storelense.soh.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
@Tag(name = "Transfers", description = "Inter-store EPC inventory transfers")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Create a new inter-store EPC transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> createTransfer(
            @Valid @RequestBody CreateTransferRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        // Non-admin users can only initiate transfers from their own store
        UUID effectiveSource = principal.isAdmin() ? req.sourceStoreId() : principal.storeId();
        CreateTransferRequest enforced = new CreateTransferRequest(
                effectiveSource, req.destStoreId(), req.type(), req.epcs());
        TransferResponse response = transferService.createTransfer(enforced, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transfer created", response));
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer details by ID")
    public ResponseEntity<ApiResponse<TransferResponse>> getTransfer(@PathVariable UUID transferId) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.getTransfer(transferId)));
    }

    @PostMapping("/{transferId}/receive")
    @Operation(summary = "Record receipt of a transfer at the destination store")
    public ResponseEntity<ApiResponse<TransferResponse>> receiveTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody ReceiveTransferRequest req) {

        return ResponseEntity.ok(ApiResponse.ok(transferService.receiveTransfer(transferId, req)));
    }
}
