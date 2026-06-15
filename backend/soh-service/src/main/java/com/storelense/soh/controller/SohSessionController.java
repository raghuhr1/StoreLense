package com.storelense.soh.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.soh.dto.*;
import com.storelense.soh.service.SessionParticipantService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/soh/sessions")
@RequiredArgsConstructor
@Tag(name = "SOH Sessions", description = "Stock on Hand count session management")
public class SohSessionController {

    private final SohSessionService        sessionService;
    private final SessionParticipantService participantService;

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
    public ResponseEntity<ApiResponse<?>> complete(@PathVariable UUID id) {
        long waiting = participantService.countActive(id);
        if (waiting > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("WAITING_FOR_DEVICES",
                            "Waiting for " + waiting + " device(s) to finish their zone scan"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Session completed", sessionService.completeSession(id)));
    }

    // ── Phase 5: Session Participants ─────────────────────────────────────────

    @PostMapping("/{id}/participants")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Register a device as a session participant, optionally claiming a zone")
    public ResponseEntity<ApiResponse<ParticipantResponse>> join(
            @PathVariable UUID id,
            @Valid @RequestBody JoinSessionRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(participantService.join(id, req, principal.userId())));
    }

    @PostMapping("/{id}/participants/{deviceId}/done")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "Mark a device's zone scan as complete; returns isLastActive flag")
    public ResponseEntity<ApiResponse<MarkDoneResponse>> markDone(
            @PathVariable UUID id,
            @PathVariable String deviceId) {
        return ResponseEntity.ok(ApiResponse.ok(participantService.markDone(id, deviceId)));
    }

    @GetMapping("/{id}/participants")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE')")
    @Operation(summary = "List all session participants with active/done counts")
    public ResponseEntity<ApiResponse<ParticipantsListResponse>> participants(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(participantService.list(id)));
    }

    @GetMapping("/{id}/epcs")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Get all EPCs scanned during a SOH session")
    public ResponseEntity<ApiResponse<List<String>>> getEpcs(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getSessionEpcs(id)));
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
