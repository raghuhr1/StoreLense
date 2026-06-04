package com.storelense.rfid.processing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a reader UUID to its assigned zone UUID by calling store-service.
 * Cached in Redis for 60 minutes — zone assignments rarely change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneMappingService {

    private final StringRedisTemplate redis;
    private final RestClient          storeServiceClient;

    private static final String   CACHE_KEY   = "rfid:reader:zone:%s";
    private static final String   NO_ZONE     = "NONE";
    private static final Duration CACHE_TTL   = Duration.ofMinutes(60);

    public Optional<UUID> resolveZone(UUID readerId) {
        if (readerId == null) return Optional.empty();

        String key    = String.format(CACHE_KEY, readerId);
        String cached = redis.opsForValue().get(key);

        if (cached != null) {
            return NO_ZONE.equals(cached) ? Optional.empty() : Optional.of(UUID.fromString(cached));
        }

        Optional<UUID> zoneId = fetchFromStoreService(readerId);
        redis.opsForValue().set(key, zoneId.map(UUID::toString).orElse(NO_ZONE), CACHE_TTL);
        return zoneId;
    }

    public void invalidate(UUID readerId) {
        redis.delete(String.format(CACHE_KEY, readerId));
    }

    private Optional<UUID> fetchFromStoreService(UUID readerId) {
        try {
            ReaderResponse resp = storeServiceClient.get()
                    .uri("/api/stores/readers/{id}", readerId)
                    .retrieve()
                    .body(ReaderResponse.class);
            if (resp != null && resp.data() != null && resp.data().zoneId() != null) {
                return Optional.of(resp.data().zoneId());
            }
        } catch (RestClientException e) {
            log.debug("Zone lookup failed for reader {}: {}", readerId, e.getMessage());
        }
        return Optional.empty();
    }

    // Mirrors store-service API response
    record ReaderResponse(boolean success, ReaderData data) {}
    record ReaderData(UUID id, UUID storeId, UUID zoneId, String readerCode) {}
}
