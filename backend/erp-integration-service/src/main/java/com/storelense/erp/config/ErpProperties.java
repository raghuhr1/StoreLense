package com.storelense.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storelense.erp")
public record ErpProperties(
        String   baseUrl,
        String   apiKey,
        String   apiVersion,
        Duration connectTimeout,
        Duration readTimeout,
        int      pageSize,
        Duration productSyncInterval,
        String   inventorySyncCron,
        int      maxRetries,
        boolean  pushSohEnabled
) {
    public ErpProperties {
        if (baseUrl == null) baseUrl = "http://erp.internal/api";
        if (apiVersion == null) apiVersion = "v1";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(30);
        if (pageSize == 0) pageSize = 500;
        if (productSyncInterval == null) productSyncInterval = Duration.ofHours(6);
        if (inventorySyncCron == null) inventorySyncCron = "0 0 1 * * *";
        if (maxRetries == 0) maxRetries = 3;
    }
}
