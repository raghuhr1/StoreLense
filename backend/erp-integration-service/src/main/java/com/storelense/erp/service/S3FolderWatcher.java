package com.storelense.erp.service;

import com.storelense.erp.config.ErpImportProperties;
import com.storelense.erp.domain.repository.ErpImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storelense.erp.import.s3-enabled", havingValue = "true")
public class S3FolderWatcher {

    private final ErpImportService        importService;
    private final ErpImportBatchRepository batchRepository;
    private final ErpImportProperties     importProperties;
    private final S3Client                s3Client;

    @Scheduled(fixedDelayString = "${storelense.erp.import.s3-poll-delay-ms:60000}")
    public void poll() {
        log.debug("S3FolderWatcher: polling s3://{}/{}", importProperties.s3Bucket(), importProperties.s3Prefix());

        String continuationToken = null;
        do {
            ListObjectsV2Request req = ListObjectsV2Request.builder()
                    .bucket(importProperties.s3Bucket())
                    .prefix(importProperties.s3Prefix())
                    .continuationToken(continuationToken)
                    .build();

            ListObjectsV2Response resp = s3Client.listObjectsV2(req);

            for (S3Object obj : resp.contents()) {
                String key = obj.key();
                if (key.endsWith("/")) continue;   // skip "folder" marker objects
                if (alreadyProcessed(key)) continue;

                log.info("S3FolderWatcher: processing new key {}", key);
                importService.processFile(key);
            }

            continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    // Skip keys that have a COMPLETED or PROCESSING batch — retry keys that previously FAILED
    private boolean alreadyProcessed(String key) {
        return batchRepository.existsByFilePathAndStatusNot(key, "FAILED");
    }
}
