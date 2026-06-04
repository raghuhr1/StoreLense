package com.storelense.rfid.processing.service;

import com.storelense.rfid.processing.domain.entity.RfidSession;
import com.storelense.rfid.processing.domain.repository.RfidSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Manages RFID session state using Redis for hot path and PostgreSQL for persistence.
 *
 * Redis keys:
 *   rfid:session:{sessionId}:active   → "1"  (TTL = 25h, refreshed on each batch)
 *   rfid:session:{sessionId}:reads    → total read counter (INCR)
 *   rfid:session:{sessionId}:unique   → unique EPC HyperLogLog (PFADD/PFCOUNT)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfidSessionManager {

    private final RfidSessionRepository sessionRepository;
    private final StringRedisTemplate   redis;

    private static final Duration SESSION_TTL = Duration.ofHours(25);
    private static final String   ACTIVE_KEY  = "rfid:session:%s:active";
    private static final String   READS_KEY   = "rfid:session:%s:reads";
    private static final String   HLL_KEY     = "rfid:session:%s:unique";

    public boolean isSessionActive(UUID sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(activeKey(sessionId)));
    }

    /**
     * Registers an EPC read for this session.
     * Returns true if this EPC is new within the session (not seen before).
     */
    public boolean recordRead(UUID sessionId, String epc) {
        // Increment total read counter
        redis.opsForValue().increment(readsKey(sessionId));
        redis.expire(readsKey(sessionId), SESSION_TTL);

        // HyperLogLog for unique EPC estimation — O(1), space-efficient
        Long added = redis.opsForHyperLogLog().add(hllKey(sessionId), epc);
        redis.expire(hllKey(sessionId), SESSION_TTL);
        redis.expire(activeKey(sessionId), SESSION_TTL); // refresh TTL

        return added != null && added > 0;
    }

    public long getTotalReads(UUID sessionId) {
        String val = redis.opsForValue().get(readsKey(sessionId));
        return val != null ? Long.parseLong(val) : 0;
    }

    public long getUniqueEpcEstimate(UUID sessionId) {
        return redis.opsForHyperLogLog().size(hllKey(sessionId));
    }

    @Transactional
    public void flushSessionStats(UUID sessionId) {
        long reads  = getTotalReads(sessionId);
        long unique = getUniqueEpcEstimate(sessionId);

        if (reads > 0) {
            sessionRepository.incrementCounts(sessionId, (int) reads, (int) unique);
            redis.delete(readsKey(sessionId));
            redis.delete(hllKey(sessionId));
            log.debug("Flushed session {} stats: reads={} unique={}", sessionId, reads, unique);
        }
    }

    @Transactional
    public void markSessionOpen(UUID sessionId, UUID storeId, UUID sohSessionId, String deviceId) {
        redis.opsForValue().set(activeKey(sessionId), "1", SESSION_TTL);

        // Ensure session exists in DB
        if (!sessionRepository.existsById(sessionId)) {
            sessionRepository.save(RfidSession.builder()
                    .id(sessionId)
                    .storeId(storeId)
                    .sohSessionId(sohSessionId)
                    .deviceId(deviceId)
                    .build());
        }
    }

    @Transactional
    public void closeSession(UUID sessionId) {
        flushSessionStats(sessionId);
        sessionRepository.closeSession(sessionId, OffsetDateTime.now());
        redis.delete(activeKey(sessionId));
        log.info("RFID session {} closed", sessionId);
    }

    private String activeKey(UUID id) { return String.format(ACTIVE_KEY, id); }
    private String readsKey(UUID id)  { return String.format(READS_KEY, id); }
    private String hllKey(UUID id)    { return String.format(HLL_KEY, id); }
}
