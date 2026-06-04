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
 * Resolves an EPC to an internal product UUID.
 *
 * Resolution order:
 *   1. Redis cache  (TTL: 30 min)
 *   2. product-service REST call
 *   3. EPC decode → GTIN → product-service GTIN lookup
 *   4. null (unknown EPC)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpcResolutionService {

    private final StringRedisTemplate  redis;
    private final RestClient           productServiceClient;
    private final EpcDecoderService    epcDecoder;

    private static final String CACHE_PREFIX    = "rfid:epc:product:";
    private static final String UNKNOWN_SENTINEL = "UNKNOWN";
    private static final Duration CACHE_TTL      = Duration.ofMinutes(30);
    private static final Duration UNKNOWN_TTL    = Duration.ofMinutes(5);

    public Optional<UUID> resolveToProductId(String epc) {
        String cacheKey = CACHE_PREFIX + epc;

        // 1. Redis cache
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            if (UNKNOWN_SENTINEL.equals(cached)) return Optional.empty();
            return Optional.of(UUID.fromString(cached));
        }

        // 2. product-service direct EPC lookup
        Optional<UUID> fromApi = lookupByEpc(epc);
        if (fromApi.isPresent()) {
            redis.opsForValue().set(cacheKey, fromApi.get().toString(), CACHE_TTL);
            return fromApi;
        }

        // 3. Decode EPC → GTIN, then lookup by GTIN barcode
        Optional<UUID> fromGtin = epcDecoder.decode(epc)
                .flatMap(decoded -> lookupByGtin(decoded.gtin14()));

        if (fromGtin.isPresent()) {
            redis.opsForValue().set(cacheKey, fromGtin.get().toString(), CACHE_TTL);
            return fromGtin;
        }

        // 4. Cache the miss to avoid hammering the API for unknown EPCs
        redis.opsForValue().set(cacheKey, UNKNOWN_SENTINEL, UNKNOWN_TTL);
        log.debug("EPC {} could not be resolved to any product", epc);
        return Optional.empty();
    }

    public void invalidateCache(String epc) {
        redis.delete(CACHE_PREFIX + epc);
    }

    private Optional<UUID> lookupByEpc(String epc) {
        try {
            EpcLookupResponse resp = productServiceClient.get()
                    .uri("/api/products/epc/{epc}", epc)
                    .retrieve()
                    .body(EpcLookupResponse.class);
            if (resp != null && resp.success() && resp.data() != null) {
                return Optional.ofNullable(resp.data().productId());
            }
        } catch (RestClientException e) {
            log.warn("EPC lookup failed for {}: {}", epc, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<UUID> lookupByGtin(String gtin) {
        try {
            EpcLookupResponse resp = productServiceClient.get()
                    .uri("/api/products/epc/{epc}", gtin)   // re-uses same endpoint
                    .retrieve()
                    .body(EpcLookupResponse.class);
            if (resp != null && resp.success() && resp.data() != null) {
                return Optional.ofNullable(resp.data().productId());
            }
        } catch (RestClientException e) {
            log.debug("GTIN lookup failed for {}: {}", gtin, e.getMessage());
        }
        return Optional.empty();
    }

    // Mirrors product-service API response shape
    record EpcLookupResponse(boolean success, EpcData data) {}
    record EpcData(String epc, UUID productId, boolean fromCache) {}
}
