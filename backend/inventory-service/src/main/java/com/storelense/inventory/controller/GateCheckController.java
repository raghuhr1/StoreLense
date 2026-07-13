package com.storelense.inventory.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.inventory.dto.GateCheckDto;
import com.storelense.inventory.dto.GateCheckRequest;
import com.storelense.inventory.dto.GateCheckSummaryDto;
import com.storelense.inventory.service.GateCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/gate/checks")
@RequiredArgsConstructor
@Tag(name = "Gate Checks", description = "C66 guard gate check events")
public class GateCheckController {

    private final GateCheckService gateCheckService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE','SECURITY_GUARD')")
    @Operation(summary = "Record a gate check event from the C66 app")
    public ResponseEntity<ApiResponse<GateCheckDto>> record(
            @Valid @RequestBody GateCheckRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID guardId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(ApiResponse.ok(gateCheckService.record(req, guardId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "List gate checks for dashboard")
    public ResponseEntity<ApiResponse<PageResponse<GateCheckDto>>> list(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effectiveStoreId = principal.isAdmin() && storeId != null ? storeId : principal.storeId();
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        OffsetDateTime effectiveTo   = to   != null ? to   : OffsetDateTime.now(ZoneOffset.UTC);

        var result = gateCheckService.list(effectiveStoreId, effectiveFrom, effectiveTo,
                outcome, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "KPI summary for guard dashboard")
    public ResponseEntity<ApiResponse<GateCheckSummaryDto>> summary(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal StoreLensePrincipal principal) {

        UUID effectiveStoreId = principal.isAdmin() && storeId != null ? storeId : principal.storeId();
        LocalDate effectiveDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);

        return ResponseEntity.ok(ApiResponse.ok(
                gateCheckService.summary(effectiveStoreId, effectiveDate)));
    }
}
