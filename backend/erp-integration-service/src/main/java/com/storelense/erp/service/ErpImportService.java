package com.storelense.erp.service;

import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.config.ErpImportProperties;
import com.storelense.erp.domain.entity.ErpImportBatch;
import com.storelense.erp.domain.entity.ErpSohSnapshot;
import com.storelense.erp.domain.repository.ErpImportBatchRepository;
import com.storelense.erp.domain.repository.ErpSohSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpImportService {

    private final ErpImportBatchRepository      batchRepository;
    private final ErpSohSnapshotRepository      snapshotRepository;
    private final ErpCsvParser                  csvParser;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ErpImportProperties           importProperties;
    private final EanResolutionService          eanResolutionService;

    // Self-injection through Spring proxy so @Transactional on commitImport is honoured
    @Lazy @Autowired
    private ErpImportService self;

    // Optional: only present when storelense.erp.import.s3-enabled=true
    @Autowired(required = false)
    private S3Client s3Client;

    // ── Public entry points ─────────────────────────────────────────────────

    public ErpImportBatch processFile(Path localPath) {
        log.info("Processing local ERP import: {}", localPath);
        ErpImportBatch batch = createBatch("FILE", localPath.toString());
        try (InputStream is = Files.newInputStream(localPath)) {
            List<ErpSohSnapshot> snapshots = csvParser.parse(is, batch);
            return self.commitImport(batch, snapshots);
        } catch (Exception e) {
            log.error("ERP import failed [{}]: {}", localPath, e.getMessage(), e);
            return self.markFailed(batch.getId(), e.getMessage());
        }
    }

    public ErpImportBatch processFile(String s3Key) {
        log.info("Processing S3 ERP import: {}", s3Key);
        if (s3Client == null) {
            throw new IllegalStateException(
                    "S3 client not configured — set storelense.erp.import.s3-enabled=true");
        }
        ErpImportBatch batch = createBatch("S3", s3Key);
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(importProperties.s3Bucket())
                .key(s3Key)
                .build();
        try (InputStream is = s3Client.getObject(req)) {
            List<ErpSohSnapshot> snapshots = csvParser.parse(is, batch);
            return self.commitImport(batch, snapshots);
        } catch (Exception e) {
            log.error("ERP import failed [s3:{}]: {}", s3Key, e.getMessage(), e);
            return self.markFailed(batch.getId(), e.getMessage());
        }
    }

    // ── Transactional helpers (called via proxy through `self`) ─────────────

    @Transactional
    public ErpImportBatch commitImport(ErpImportBatch batch, List<ErpSohSnapshot> snapshots)
            throws IOException {
        snapshotRepository.saveAll(snapshots);

        eanResolutionService.resolveAll(batch.getId());

        long resolved   = snapshotRepository.countByBatch_IdAndResolutionStatus(batch.getId(), "RESOLVED");
        long unresolved = snapshotRepository.countUnresolvedByBatchId(batch.getId());

        batch.setStatus("COMPLETED");
        batch.setTotalRows(snapshots.size());
        batch.setResolvedRows((int) resolved);
        batch.setUnresolvedRows((int) unresolved);
        batch.setImportedAt(OffsetDateTime.now());
        ErpImportBatch saved = batchRepository.save(batch);

        publishEvent(saved, snapshots);
        log.info("ERP import committed: batchId={} rows={} resolved={} unresolved={}",
                saved.getId(), snapshots.size(), resolved, unresolved);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ErpImportBatch markFailed(UUID batchId, String error) {
        return batchRepository.findById(batchId).map(b -> {
            b.setStatus("FAILED");
            b.setErrorMessage(error);
            return batchRepository.save(b);
        }).orElseThrow();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private ErpImportBatch createBatch(String sourceType, String filePath) {
        return batchRepository.save(ErpImportBatch.builder()
                .storeId(importProperties.storeId())
                .sourceType(sourceType)
                .filePath(filePath)
                .status("PROCESSING")
                .build());
    }

    private void publishEvent(ErpImportBatch batch, List<ErpSohSnapshot> snapshots) {
        String zoneRegion = snapshots.isEmpty() ? null : snapshots.get(0).getZoneRegion();
        kafkaTemplate.send(
                KafkaTopics.ERP_IMPORT_COMPLETED,
                batch.getStoreId().toString(),
                new ErpImportCompletedEvent(
                        UUID.randomUUID().toString(),
                        batch.getId(),
                        batch.getStoreId(),
                        Instant.now(),
                        snapshots.size(),
                        batch.getResolvedRows(),
                        batch.getUnresolvedRows(),
                        zoneRegion
                )
        );
    }
}
