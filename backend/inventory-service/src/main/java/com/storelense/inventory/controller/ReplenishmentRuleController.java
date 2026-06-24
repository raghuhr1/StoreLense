package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.ReplenishmentRuleRequest;
import com.storelense.inventory.dto.ReplenishmentRuleResponse;
import com.storelense.inventory.dto.ReplenishmentSuggestion;
import com.storelense.inventory.service.ReplenishmentRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/replenishment-rules")
@RequiredArgsConstructor
@Tag(name = "Replenishment Rules", description = "Auto-trigger configuration for refill task creation")
public class ReplenishmentRuleController {

    private final ReplenishmentRuleService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "List active replenishment rules for a store")
    public ResponseEntity<ApiResponse<List<ReplenishmentRuleResponse>>> list(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.list(effective)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Create or update a replenishment rule (upsert by store+trigger_status)")
    public ResponseEntity<ApiResponse<ReplenishmentRuleResponse>> upsert(
            @Valid @RequestBody ReplenishmentRuleRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID storeId = principal.isAdmin() ? req.storeId() : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.upsert(storeId, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Soft-delete a replenishment rule (sets active=false)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        service.delete(id, effective);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suggest")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Compute replenishment suggestions from current scan state",
               description = "Returns zone+product lines that match a rule and are below par. " +
                             "hasOpenTask=true means a pending/in-progress refill task already covers that line.")
    public ResponseEntity<ApiResponse<List<ReplenishmentSuggestion>>> suggest(
            @RequestParam UUID storeId,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(service.getSuggestions(effective)));
    }
}
