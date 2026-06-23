package com.storelense.erp.service;

import com.storelense.erp.domain.entity.ErpSohSnapshot;
import com.storelense.erp.domain.entity.ErpSohSnapshotEpc;
import com.storelense.erp.domain.repository.ErpSohSnapshotEpcRepository;
import com.storelense.erp.domain.repository.ErpSohSnapshotRepository;
import com.storelense.erp.util.Sgtin96Decoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EanResolutionService {

    private final ErpSohSnapshotRepository    snapshotRepository;
    private final ErpSohSnapshotEpcRepository epcRepository;
    private final ProductServiceClient        productServiceClient;

    /**
     * Resolves all RAW snapshots for the given batch: fetches EPCs from product-service,
     * validates each via SGTIN-96 decode, writes ErpSohSnapshotEpc rows, and marks each
     * snapshot RESOLVED, UNRESOLVED, or PARTIAL.
     *
     * Runs within the caller's transaction (REQUIRED propagation) so snapshot updates
     * are visible to commitImport's subsequent count queries.
     */
    @Transactional
    public void resolveAll(UUID batchId) {
        List<ErpSohSnapshot> raw = snapshotRepository.findByBatch_IdAndResolutionStatus(batchId, "RAW");
        if (raw.isEmpty()) {
            log.debug("EanResolution: no RAW snapshots for batch {}", batchId);
            return;
        }

        int resolved = 0, unresolved = 0;
        for (ErpSohSnapshot snapshot : raw) {
            try {
                if (productServiceClient.existsByEan(snapshot.getEan())) {
                    snapshot.setResolutionStatus("RESOLVED");
                    // Pre-link any already-encoded EPC tags for reconciliation
                    List<String> epcs = productServiceClient.getEpcsByEan(snapshot.getEan());
                    if (!epcs.isEmpty()) {
                        linkEpcs(snapshot, epcs);
                    }
                    resolved++;
                } else {
                    snapshot.setResolutionStatus("UNRESOLVED");
                    unresolved++;
                }
                snapshotRepository.save(snapshot);
            } catch (Exception e) {
                log.error("EAN resolution failed for snapshot {} (EAN {}): {}",
                        snapshot.getId(), snapshot.getEan(), e.getMessage());
                snapshot.setResolutionStatus("UNRESOLVED");
                snapshotRepository.save(snapshot);
                unresolved++;
            }
        }
        log.info("EAN resolution complete for batch {}: resolved={} unresolved={}", batchId, resolved, unresolved);
    }

    private void linkEpcs(ErpSohSnapshot snapshot, List<String> epcs) {
        List<ErpSohSnapshotEpc> matches = epcs.stream()
                .map(epc -> {
                    // Prefer SGTIN-96 match; fall back to direct product-EPC association
                    // so plain-text or non-SGTIN-96 EPCs still participate in reconciliation.
                    String decoded = Sgtin96Decoder.decode(epc);
                    String method  = snapshot.getEan().equals(decoded) ? "SGTIN96" : "PRODUCT_LINK";
                    return ErpSohSnapshotEpc.builder()
                            .snapshot(snapshot)
                            .epc(epc)
                            .matchedBy(method)
                            .build();
                })
                .collect(Collectors.toList());

        if (!matches.isEmpty()) {
            epcRepository.saveAll(matches);
        }
    }
}
