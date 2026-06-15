package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, UUID> {

    List<SessionParticipant> findBySessionIdOrderByJoinedAtAsc(UUID sessionId);

    Optional<SessionParticipant> findBySessionIdAndDeviceId(UUID sessionId, String deviceId);

    long countBySessionIdAndStatus(UUID sessionId, String status);

    boolean existsBySessionIdAndZoneRegionAndStatus(UUID sessionId, String zoneRegion, String status);
}
