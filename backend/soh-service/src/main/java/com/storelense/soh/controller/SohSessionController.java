package com.storelense.soh.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.soh.dto.*;
import com.storelense.soh.service.SohSessionService;
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
@RequestMapping("/api/soh/sessions")
@RequiredArgsConstructor
@Tag(name = "SOH Sessions", description = "Stock on Hand count session management")
public class SohSessionController {

    private final SohSessionService sessionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "List SOH sessions for a store")
    public ResponseEntity<ApiResponse<PageResponse<SohSessionResponse>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(sessionService.listSessions(effective, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    public ResponseEntity<ApiResponse<SohSessionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getSession(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Start a new SOH count session")
    public ResponseEntity<ApiResponse<SohSessionResponse>> start(
            @Valid @RequestBody StartSessionRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Session started", sessionService.startSession(req, principal.userId())));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Complete a SOH session and generate result")
    public ResponseEntity<ApiResponse<SohResultResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Session completed", sessionService.completeSession(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Cancel a SOH session")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        sessionService.cancelSession(id, reason);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
