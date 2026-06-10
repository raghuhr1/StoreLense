package com.storelense.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@ConfigurationProperties(prefix = "storelense.erp.import")
public record ErpImportProperties(
        UUID    storeId,
        boolean localEnabled,
        String  localFolder,
        boolean s3Enabled,
        String  s3Bucket,
        String  s3Prefix,
        String  s3Region,
        long    s3PollDelayMs
) {
    public ErpImportProperties {
        if (localFolder   == null) localFolder   = "/tmp/erp-import";
        if (s3Prefix      == null) s3Prefix      = "erp/import/";
        if (s3Region      == null) s3Region      = "us-east-1";
        if (s3PollDelayMs == 0L)   s3PollDelayMs = 60_000L;
    }
}
