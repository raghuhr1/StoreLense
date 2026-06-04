package com.storelense.refill.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.security.StoreLensePrincipal;
import com.storelense.refill.dto.*;
import com.storelense.refill.service.RefillTaskService;
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
@RequestMapping("/api/refill/tasks")
@RequiredArgsConstructor
@Tag(name = "Refill Tasks", description = "Refill task management and assignment")
public class RefillTaskController {

    private final RefillTaskService taskService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','REFILL_ASSOCIATE')")
    public ResponseEntity<ApiResponse<PageResponse<RefillTaskResponse>>> list(
            @RequestParam UUID storeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        UUID effective = principal.isAdmin() ? storeId : principal.storeId();
        return ResponseEntity.ok(ApiResponse.ok(taskService.listTasks(effective, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','REFILL_ASSOCIATE')")
    public ResponseEntity<ApiResponse<RefillTaskResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTask(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Create a refill task")
    public ResponseEntity<ApiResponse<RefillTaskResponse>> create(
            @Valid @RequestBody CreateRefillTaskRequest req,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Task created", taskService.createTask(req, principal.userId())));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    @Operation(summary = "Assign a task to a refill associate")
    public ResponseEntity<ApiResponse<RefillTaskResponse>> assign(
            @PathVariable UUID id,
            @RequestParam UUID assignedTo,
            @AuthenticationPrincipal StoreLensePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.assignTask(id, assignedTo, principal.userId())));
    }

    @PatchMapping("/{taskId}/items/{itemId}/fulfil")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','REFILL_ASSOCIATE')")
    @Operation(summary = "Record fulfilment quantity for a task item")
    public ResponseEntity<ApiResponse<RefillTaskResponse>> fulfil(
            @PathVariable UUID taskId,
            @PathVariable UUID itemId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.updateItemFulfilment(taskId, itemId, quantity)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        taskService.cancelTask(id, reason);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
