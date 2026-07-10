package com.storelense.soh.service;

import com.storelense.soh.domain.repository.SohSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Automatically closes sessions that remain in_progress or paused beyond the
 * configurable staleness threshold. Protects against devices that go offline
 * without explicitly completing or cancelling their session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpiryJob {

    private final SohSessionRepository sessionRepository;

    @Value("${storelense.session.stale-hours:8}")
    private int staleHours;

    @Scheduled(fixedDelayString = "${storelense.session.expiry-check-ms:900000}")
    @Transactional
    public void expireStale() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(staleHours);
        var stale = sessionRepository.findAllStaleActiveSessions(cutoff);

        if (stale.isEmpty()) return;

        log.info("SessionExpiryJob: found {} stale session(s) older than {} hours", stale.size(), staleHours);

        stale.forEach(session -> {
            String prev = session.getStatus();
            session.setStatus("cancelled");
            session.setCancelledAt(OffsetDateTime.now());
            session.setCancellationReason("Auto-expired after " + staleHours + "h of inactivity (was: " + prev + ")");
            sessionRepository.save(session);
            log.warn("Auto-cancelled stale session {} [store={} location={}] — was {} since {}",
                    session.getId(), session.getStoreId(), session.getLocationCode(),
                    prev, session.getStartedAt());
        });
    }
}
