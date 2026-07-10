package com.storelense.rfid.processing.service;

import com.storelense.rfid.processing.domain.entity.AntennaLocationMapping;
import com.storelense.rfid.processing.domain.repository.AntennaLocationMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves (readerId, antennaPort) → (locationCode, sectionCode) with a 60-minute
 * Redis cache so each RFID read does not hit the database.
 *
 * Cache key: rfid:antenna:{readerId}:{antennaPort}
 * Cache value: "SALES_FLOOR|WOMENS"  or  "BACKROOM|"  (pipe-delimited; empty section = null)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationMappingService {

    static final String CACHE_PREFIX = "rfid:antenna:";
    static final String NOT_FOUND    = "__NONE__";
    static final Duration TTL        = Duration.ofMinutes(60);

    private final AntennaLocationMappingRepository mappingRepository;
    private final StringRedisTemplate              redis;

    public record LocationPair(String locationCode, String sectionCode) {}

    public Optional<LocationPair> resolve(String readerId, Short antennaPort) {
        if (readerId == null || antennaPort == null) return Optional.empty();

        String key = CACHE_PREFIX + readerId + ":" + antennaPort;

        // Cache hit
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            if (NOT_FOUND.equals(cached)) return Optional.empty();
            return Optional.of(decode(cached));
        }

        // Cache miss — query DB
        Optional<AntennaLocationMapping> mapping =
                mappingRepository.findByReaderIdAndAntennaPortAndIsActiveTrue(readerId, antennaPort);

        if (mapping.isPresent()) {
            AntennaLocationMapping m = mapping.get();
            String encoded = encode(m.getLocationCode(), m.getSectionCode());
            redis.opsForValue().set(key, encoded, TTL);
            return Optional.of(decode(encoded));
        }

        // Store negative cache entry to avoid repeated DB hits for unknown antennas
        redis.opsForValue().set(key, NOT_FOUND, TTL);
        log.debug("No antenna-location mapping found for reader={} port={}", readerId, antennaPort);
        return Optional.empty();
    }

    /** Evict cache for a single antenna (call after admin updates a mapping). */
    public void evict(String readerId, Short antennaPort) {
        redis.delete(CACHE_PREFIX + readerId + ":" + antennaPort);
    }

    private static String encode(String locationCode, String sectionCode) {
        return locationCode + "|" + (sectionCode != null ? sectionCode : "");
    }

    private static LocationPair decode(String encoded) {
        int pipe = encoded.indexOf('|');
        String loc = encoded.substring(0, pipe);
        String sec = encoded.substring(pipe + 1);
        return new LocationPair(loc, sec.isEmpty() ? null : sec);
    }
}
