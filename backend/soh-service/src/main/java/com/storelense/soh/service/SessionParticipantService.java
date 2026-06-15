package com.storelense.soh.service;

import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.soh.domain.entity.SessionParticipant;
import com.storelense.soh.domain.repository.SessionParticipantRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
import com.storelense.soh.dto.JoinSessionRequest;
import com.storelense.soh.dto.MarkDoneResponse;
import com.storelense.soh.dto.ParticipantResponse;
import com.storelense.soh.dto.ParticipantsListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionParticipantService {

    private final SessionParticipantRepository repo;
    private final SohSessionRepository         sessionRepo;

    @Transactional
    public ParticipantResponse join(UUID sessionId, JoinSessionRequest req, UUID userId) {
        if (!sessionRepo.existsById(sessionId)) {
            throw new ResourceNotFoundException("SohSession", sessionId);
        }

        // Zone-exclusivity check: block if another ACTIVE participant already claimed this zone.
        // Allow the same device to re-join (e.g., after a reconnect) with the same zone.
        if (req.zoneRegion() != null &&
            repo.existsBySessionIdAndZoneRegionAndStatus(sessionId, req.zoneRegion(), "active")) {

            boolean isOwnZone = repo.findBySessionIdAndDeviceId(sessionId, req.deviceId())
                    .map(p -> req.zoneRegion().equals(p.getZoneRegion()))
                    .orElse(false);

            if (!isOwnZone) {
                throw new BusinessException(
                        "ZONE_TAKEN",
                        "Zone '" + req.zoneRegion() + "' is already claimed by another device",
                        HttpStatus.CONFLICT
                );
            }
        }

        // Upsert: reactivate a device that previously left or marked done.
        SessionParticipant participant = repo.findBySessionIdAndDeviceId(sessionId, req.deviceId())
                .map(p -> {
                    p.setStatus("active");
                    p.setZoneRegion(req.zoneRegion());
                    p.setCompletedAt(null);
                    return p;
                })
                .orElseGet(() -> SessionParticipant.builder()
                        .sessionId(sessionId)
                        .deviceId(req.deviceId())
                        .userId(userId)
                        .zoneRegion(req.zoneRegion())
                        .status("active")
                        .build());

        log.info("Device {} joined session {} (zone={})", req.deviceId(), sessionId, req.zoneRegion());
        return toResponse(repo.save(participant));
    }

    @Transactional
    public MarkDoneResponse markDone(UUID sessionId, String deviceId) {
        SessionParticipant p = repo.findBySessionIdAndDeviceId(sessionId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionParticipant",
                        sessionId + "/" + deviceId));

        p.setStatus("done");
        p.setCompletedAt(OffsetDateTime.now());
        repo.save(p);

        long remaining = repo.countBySessionIdAndStatus(sessionId, "active");
        log.info("Device {} marked done in session {}; {} device(s) still active", deviceId, sessionId, remaining);
        return new MarkDoneResponse("done", remaining == 0, (int) remaining);
    }

    @Transactional(readOnly = true)
    public ParticipantsListResponse list(UUID sessionId) {
        List<ParticipantResponse> participants = repo
                .findBySessionIdOrderByJoinedAtAsc(sessionId)
                .stream()
                .map(this::toResponse)
                .toList();

        long active = repo.countBySessionIdAndStatus(sessionId, "active");
        long done   = repo.countBySessionIdAndStatus(sessionId, "done");
        return new ParticipantsListResponse(participants, (int) active, (int) done);
    }

    @Transactional(readOnly = true)
    public long countActive(UUID sessionId) {
        return repo.countBySessionIdAndStatus(sessionId, "active");
    }

    private ParticipantResponse toResponse(SessionParticipant p) {
        return new ParticipantResponse(
                p.getId(), p.getDeviceId(), p.getZoneRegion(),
                p.getStatus(), p.getJoinedAt(), p.getCompletedAt()
        );
    }
}
