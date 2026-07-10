package com.storelense.soh.service;

import com.storelense.common.exception.BusinessException;
import com.storelense.soh.domain.entity.SohSession;
import com.storelense.soh.domain.repository.SohSessionItemRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
import com.storelense.soh.domain.repository.StoreLocationRepository;
import com.storelense.soh.mapper.SohMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SohSessionPauseResumeTest {

    @Mock SohSessionRepository     sessionRepository;
    @Mock SohSessionItemRepository itemRepository;
    @Mock StoreLocationRepository  locationRepository;
    @Mock SohMapper                sohMapper;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock JdbcClient               jdbcClient;
    @Mock CycleCountService        cycleCountService;

    @InjectMocks SohSessionService service;

    // ── pauseSession ───────────────────────────────────────────────────────────

    @Test
    void pauseSession_setsStatusPaused_andTimestamp_whenInProgress() {
        SohSession session = sessionWithStatus("in_progress");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        service.pauseSession(session.getId());

        ArgumentCaptor<SohSession> captor = ArgumentCaptor.forClass(SohSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("paused");
        assertThat(captor.getValue().getPausedAt()).isNotNull();
    }

    @Test
    void pauseSession_throwsBusinessException_whenAlreadyPaused() {
        SohSession session = sessionWithStatus("paused");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.pauseSession(session.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("in_progress");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void pauseSession_throwsBusinessException_whenCompleted() {
        SohSession session = sessionWithStatus("completed");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.pauseSession(session.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // ── resumeSession ──────────────────────────────────────────────────────────

    @Test
    void resumeSession_setsStatusInProgress_andTimestamp_whenPaused() {
        SohSession session = sessionWithStatus("paused");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        service.resumeSession(session.getId());

        ArgumentCaptor<SohSession> captor = ArgumentCaptor.forClass(SohSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("in_progress");
        assertThat(captor.getValue().getResumedAt()).isNotNull();
    }

    @Test
    void resumeSession_throwsBusinessException_whenInProgress() {
        SohSession session = sessionWithStatus("in_progress");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.resumeSession(session.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("paused");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void resumeSession_throwsBusinessException_whenCompleted() {
        SohSession session = sessionWithStatus("completed");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.resumeSession(session.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // ── uploadSession ──────────────────────────────────────────────────────────

    @Test
    void uploadSession_setsStatusUploaded_whenCompleted() {
        SohSession session = sessionWithStatus("completed");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        service.uploadSession(session.getId());

        ArgumentCaptor<SohSession> captor = ArgumentCaptor.forClass(SohSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("uploaded");
        assertThat(captor.getValue().getUploadedAt()).isNotNull();
    }

    @Test
    void uploadSession_throwsBusinessException_whenNotCompleted() {
        SohSession session = sessionWithStatus("in_progress");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.uploadSession(session.getId()))
                .isInstanceOf(BusinessException.class);

        verify(sessionRepository, never()).save(any());
    }

    // ── locationConflicts (tested indirectly via startSession) ────────────────

    @Test
    void startSession_throwsConflict_whenActiveSessionExistsForSameLocation() {
        UUID storeId = UUID.randomUUID();
        SohSession existing = sessionWithStatus("in_progress");
        existing.setLocationCode("SALES_FLOOR");
        existing.setSectionCode("MENS");
        existing.setStoreId(storeId);

        when(sessionRepository.findActiveSessions(storeId)).thenReturn(java.util.List.of(existing));

        var req = new com.storelense.soh.dto.StartSessionRequest(
                storeId, null, "cycle_count", null, null, null,
                UUID.randomUUID(), "SALES_FLOOR", "MENS"
        );

        when(locationRepository.existsByStoreIdAndLocationCodeAndSectionCodeAndIsActiveTrue(
                any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.startSession(req, UUID.randomUUID(), storeId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SESSION_ACTIVE");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private SohSession sessionWithStatus(String status) {
        SohSession s = new SohSession();
        s.setId(UUID.randomUUID());
        s.setStoreId(UUID.randomUUID());
        s.setStatus(status);
        s.setSessionType("cycle_count");
        s.setTotalEpcReads(0);
        s.setUniqueEpcCount(0);
        return s;
    }
}
