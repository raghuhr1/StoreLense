package com.storelense.rfid.processing.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.rfid.processing.consumer.RfidDlqConsumer;
import com.storelense.rfid.processing.dto.DlqRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rfid/dlq")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "RFID DLQ", description = "Dead Letter Queue management for failed RFID reads")
public class RfidDlqController {

    private final RfidDlqConsumer dlqConsumer;

    @GetMapping
    @Operation(summary = "List recently failed RFID read events (up to 1000)")
    public ResponseEntity<ApiResponse<List<DlqRecord>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(dlqConsumer.getRecentFailures()));
    }

    @PostMapping("/{key}/replay")
    @Operation(summary = "Re-publish a failed event back to rfid.reads.raw")
    public ResponseEntity<ApiResponse<Void>> replay(@PathVariable String key) {
        boolean ok = dlqConsumer.replay(key);
        if (!ok) {
            return ResponseEntity.ok(ApiResponse.error("NOT_FOUND",
                    "No DLQ record found with key: " + key));
        }
        return ResponseEntity.ok(ApiResponse.ok("Event replayed", null));
    }
}
