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
                List<String> epcs = productServiceClient.getEpcsByEan(snapshot.getEan());
                if (epcs.isEmpty()) {
                    snapshot.setResolutionStatus("UNRESOLVED");
                    unresolved++;
                } else {
                    boolean ok = resolveSnapshot(snapshot, epcs);
                    if (ok) resolved++; else unresolved++;
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

    private boolean resolveSnapshot(ErpSohSnapshot snapshot, List<String> epcs) {
        List<ErpSohSnapshotEpc> matches = epcs.stream()
                .filter(epc -> snapshot.getEan().equals(Sgtin96Decoder.decode(epc)))
                .map(epc -> ErpSohSnapshotEpc.builder()
                        .snapshot(snapshot)
                        .epc(epc)
                        .matchedBy("SGTIN96")
                        .build())
                .collect(Collectors.toList());

        if (!matches.isEmpty()) {
            epcRepository.saveAll(matches);
            snapshot.setResolutionStatus("RESOLVED");
            return true;
        }
        // Product has EPC tags but none decode to this EAN via SGTIN-96
        snapshot.setResolutionStatus("UNRESOLVED");
        return false;
    }
}
