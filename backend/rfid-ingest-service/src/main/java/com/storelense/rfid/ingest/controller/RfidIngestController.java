package com.storelense.rfid.ingest.controller;

import com.storelense.common.dto.ApiResponse;
import com.storelense.rfid.ingest.dto.RfidReadBatchRequest;
import com.storelense.rfid.ingest.service.RfidIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rfid/ingest")
@RequiredArgsConstructor
@Tag(name = "RFID Ingest", description = "Receive raw EPC reads from Zebra devices")
public class RfidIngestController {

    private final RfidIngestService ingestService;

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','STORE_ASSOCIATE','REFILL_ASSOCIATE')")
    @Operation(summary = "Submit a batch of EPC reads from a scan session")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> ingestBatch(
            @Valid @RequestBody RfidReadBatchRequest batch,
            HttpServletRequest request) {

        String correlationId = request.getHeader("X-Correlation-Id");
        int published = ingestService.ingestBatch(batch, correlationId);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Batch accepted", Map.of("published", published)));
    }
}
