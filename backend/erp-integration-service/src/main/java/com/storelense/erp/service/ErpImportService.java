package com.storelense.erp.service;

import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.config.ErpImportProperties;
import com.storelense.erp.domain.entity.ErpImportBatch;
import com.storelense.erp.domain.entity.ErpSohSnapshot;
import com.storelense.erp.domain.repository.ErpImportBatchRepository;
import com.storelense.erp.domain.repository.ErpSohSnapshotRepository;
import com.storelense.erp.domain.repository.ErpStoreMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
    private final ErpStoreMappingRepository     storeMappingRepository;
    private final JdbcClient                    jdbcClient;

    // Self-injection through Spring proxy so @Transactional on commitImport is honoured
    @Lazy @Autowired
    private ErpImportService self;

    // Optional: only present when storelense.erp.import.s3-enabled=true
    @Autowired(required = false)
    private S3Client s3Client;

    // ── Public entry points ─────────────────────────────────────────────────

    public ErpImportBatch processFile(Path localPath) {
        log.info("Processing local ERP import: {}", localPath);
        ErpCsvParser.ParseResult parsed;
        try (InputStream is = Files.newInputStream(localPath)) {
            parsed = csvParser.parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        UUID storeId = resolveStoreCode(parsed.storeCode());
        ErpImportBatch batch = createBatch("FILE", localPath.toString(), storeId);
        try {
            parsed.snapshots().forEach(s -> s.setBatch(batch));
            return self.commitImport(batch, parsed.snapshots());
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
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(importProperties.s3Bucket())
                .key(s3Key)
                .build();
        ErpCsvParser.ParseResult parsed;
        try (InputStream is = s3Client.getObject(req)) {
            parsed = csvParser.parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        UUID storeId = resolveStoreCode(parsed.storeCode());
        ErpImportBatch batch = createBatch("S3", s3Key, storeId);
        try {
            parsed.snapshots().forEach(s -> s.setBatch(batch));
            return self.commitImport(batch, parsed.snapshots());
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
        log.info("ERP import committed: batchId={} storeCode={} rows={} resolved={} unresolved={}",
                saved.getId(), saved.getStoreId(), snapshots.size(), resolved, unresolved);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ErpImportBatch markFailed(UUID batchId, String error) {
        ErpImportBatch b = batchRepository.findById(batchId).orElseThrow();
        b.setStatus("FAILED");
        b.setErrorMessage(error);
        return batchRepository.save(b);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private UUID resolveStoreCode(String storeCode) {
        return storeMappingRepository.findByErpStoreCode(storeCode)
                .map(m -> m.getInternalStoreId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown STORE_CODE '" + storeCode + "' — add it to erp_store_mapping first"));
    }

    private ErpImportBatch createBatch(String sourceType, String filePath, UUID storeId) {
        return batchRepository.save(ErpImportBatch.builder()
                .storeId(storeId)
                .sourceType(sourceType)
                .filePath(filePath)
                .status("PROCESSING")
                .build());
    }

    private void publishEvent(ErpImportBatch batch, List<ErpSohSnapshot> snapshots) {
        String zoneRegion = snapshots.isEmpty() ? null : snapshots.get(0).getZoneRegion();

        // Resolve EAN → productId via cross-schema query (shared Postgres DB)
        List<ErpImportCompletedEvent.ResolvedItem> resolvedItems = snapshots.stream()
                .filter(s -> "RESOLVED".equals(s.getResolutionStatus()))
                .map(s -> {
                    Optional<UUID> productId = jdbcClient.sql("""
                            SELECT p.id FROM products.products p
                            JOIN products.barcodes b ON b.product_id = p.id
                            WHERE b.barcode_value = :ean
                              AND b.barcode_type IN ('ean13','ean8','upc_a')
                              AND p.is_active = true
                            LIMIT 1
                            """)
                            .param("ean", s.getEan())
                            .query(UUID.class)
                            .optional();
                    return productId.map(pid ->
                            new ErpImportCompletedEvent.ResolvedItem(pid, s.getExpectedQty()))
                            .orElse(null);
                })
                .filter(item -> item != null)
                .toList();

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
                        zoneRegion,
                        resolvedItems
                )
        );
    }
}
