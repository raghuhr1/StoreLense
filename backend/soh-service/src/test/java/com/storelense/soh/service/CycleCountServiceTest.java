package com.storelense.soh.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.soh.domain.entity.CycleCount;
import com.storelense.soh.domain.entity.SohSession;
import com.storelense.soh.domain.repository.CycleCountRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
import com.storelense.soh.dto.CreateCycleCountRequest;
import com.storelense.soh.dto.CycleCountResponse;
import com.storelense.soh.mapper.SohMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CycleCountServiceTest {

    @Mock CycleCountRepository  cycleCountRepository;
    @Mock SohSessionRepository  sessionRepository;
    @Mock SohMapper             sohMapper;

    @InjectMocks CycleCountService service;

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_setsDraftStatus_andSavesToRepository() {
        UUID storeId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        var req      = new CreateCycleCountRequest(storeId, null, "End of season");

        CycleCount saved = CycleCount.builder()
                .id(UUID.randomUUID()).storeId(storeId).status("DRAFT")
                .countDate(LocalDate.now()).createdBy(userId).build();
        CycleCountResponse dto = mockResponse(saved);

        when(cycleCountRepository.save(any())).thenReturn(saved);
        when(sohMapper.toCycleCountResponse(saved)).thenReturn(dto);

        CycleCountResponse result = service.create(req, userId);

        ArgumentCaptor<CycleCount> captor = ArgumentCaptor.forClass(CycleCount.class);
        verify(cycleCountRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getStoreId()).isEqualTo(storeId);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(userId);
        assertThat(result).isSameAs(dto);
    }

    @Test
    void create_usesProvidedCountDate_whenNotNull() {
        LocalDate customDate = LocalDate.of(2026, 3, 15);
        UUID storeId = UUID.randomUUID();
        var req = new CreateCycleCountRequest(storeId, customDate, null);

        CycleCount saved = CycleCount.builder()
                .id(UUID.randomUUID()).storeId(storeId).countDate(customDate).status("DRAFT")
                .createdBy(UUID.randomUUID()).build();
        when(cycleCountRepository.save(any())).thenReturn(saved);
        when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(saved));

        service.create(req, UUID.randomUUID());

        ArgumentCaptor<CycleCount> captor = ArgumentCaptor.forClass(CycleCount.class);
        verify(cycleCountRepository).save(captor.capture());
        assertThat(captor.getValue().getCountDate()).isEqualTo(customDate);
    }

    @Test
    void create_defaultsCountDateToToday_whenNull() {
        var req  = new CreateCycleCountRequest(UUID.randomUUID(), null, null);
        CycleCount saved = CycleCount.builder().id(UUID.randomUUID())
                .storeId(req.storeId()).status("DRAFT").createdBy(UUID.randomUUID())
                .countDate(LocalDate.now()).build();
        when(cycleCountRepository.save(any())).thenReturn(saved);
        when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(saved));

        service.create(req, UUID.randomUUID());

        ArgumentCaptor<CycleCount> captor = ArgumentCaptor.forClass(CycleCount.class);
        verify(cycleCountRepository).save(captor.capture());
        assertThat(captor.getValue().getCountDate())
                .isNotNull()
                .isEqualTo(LocalDate.now());
    }

    // ── validateTransition (via transition()) ──────────────────────────────────

    @Test
    void transition_allowsDraftToRunning() {
        CycleCount cc = cycleCountWithStatus("DRAFT");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);
        when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(cc));

        assertThatNoException().isThrownBy(
                () -> service.transition(cc.getId(), "RUNNING", UUID.randomUUID()));
        assertThat(cc.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void transition_allowsRunningToCompleted() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);
        when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(cc));

        assertThatNoException().isThrownBy(
                () -> service.transition(cc.getId(), "COMPLETED", UUID.randomUUID()));
    }

    @Test
    void transition_allowsCompletedToUploaded() {
        CycleCount cc = cycleCountWithStatus("COMPLETED");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);
        when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(cc));

        assertThatNoException().isThrownBy(
                () -> service.transition(cc.getId(), "UPLOADED", UUID.randomUUID()));
    }

    @Test
    void transition_allowsAnyStatusToClose() {
        for (String status : List.of("DRAFT", "RUNNING", "COMPLETED", "UPLOADED", "RECONCILED")) {
            CycleCount cc = cycleCountWithStatus(status);
            when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
            when(cycleCountRepository.save(any())).thenReturn(cc);
            when(sohMapper.toCycleCountResponse(any())).thenReturn(mockResponse(cc));

            assertThatNoException()
                    .as("should allow CLOSED from %s", status)
                    .isThrownBy(() -> service.transition(cc.getId(), "CLOSED", UUID.randomUUID()));
        }
    }

    @Test
    void transition_throwsBusinessException_whenIllegalTransition() {
        CycleCount cc = cycleCountWithStatus("DRAFT");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));

        assertThatThrownBy(() -> service.transition(cc.getId(), "COMPLETED", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void transition_throwsBusinessException_forUnknownTargetStatus() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));

        assertThatThrownBy(() -> service.transition(cc.getId(), "BOGUS", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void transition_throwsResourceNotFoundException_whenCountNotFound() {
        UUID missing = UUID.randomUUID();
        when(cycleCountRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(missing, "RUNNING", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── onSessionStarted ───────────────────────────────────────────────────────

    @Test
    void onSessionStarted_advancesDraftToRunning() {
        CycleCount cc = cycleCountWithStatus("DRAFT");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);

        service.onSessionStarted(cc.getId());

        assertThat(cc.getStatus()).isEqualTo("RUNNING");
        verify(cycleCountRepository).save(cc);
    }

    @Test
    void onSessionStarted_doesNothing_whenAlreadyRunning() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));

        service.onSessionStarted(cc.getId());

        assertThat(cc.getStatus()).isEqualTo("RUNNING");
        verify(cycleCountRepository, never()).save(any());
    }

    @Test
    void onSessionStarted_doesNothing_whenCountNotFound() {
        UUID missing = UUID.randomUUID();
        when(cycleCountRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> service.onSessionStarted(missing));
        verify(cycleCountRepository, never()).save(any());
    }

    // ── onSessionCompleted ────────────────────────────────────────────────────

    @Test
    void onSessionCompleted_advancesRunningToCompleted_whenAllSessionsDone() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);

        SohSession s1 = sessionWithStatus("completed");
        SohSession s2 = sessionWithStatus("cancelled");
        when(sessionRepository.findByCycleCountIdOrderByStartedAtAsc(cc.getId()))
                .thenReturn(List.of(s1, s2));

        service.onSessionCompleted(cc.getId());

        assertThat(cc.getStatus()).isEqualTo("COMPLETED");
        verify(cycleCountRepository).save(cc);
    }

    @Test
    void onSessionCompleted_doesNotAdvance_whenSomeSessionsStillActive() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));

        SohSession s1 = sessionWithStatus("completed");
        SohSession s2 = sessionWithStatus("in_progress");
        when(sessionRepository.findByCycleCountIdOrderByStartedAtAsc(cc.getId()))
                .thenReturn(List.of(s1, s2));

        service.onSessionCompleted(cc.getId());

        assertThat(cc.getStatus()).isEqualTo("RUNNING");
        verify(cycleCountRepository, never()).save(any());
    }

    @Test
    void onSessionCompleted_doesNotAdvance_whenStatusIsNotRunning() {
        CycleCount cc = cycleCountWithStatus("DRAFT");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));

        service.onSessionCompleted(cc.getId());

        verify(sessionRepository, never()).findByCycleCountIdOrderByStartedAtAsc(any());
        verify(cycleCountRepository, never()).save(any());
    }

    @Test
    void onSessionCompleted_treatsPausedSessionAsDone_whenOthersComplete() {
        CycleCount cc = cycleCountWithStatus("RUNNING");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);

        SohSession s1 = sessionWithStatus("completed");
        SohSession s2 = sessionWithStatus("closed");
        when(sessionRepository.findByCycleCountIdOrderByStartedAtAsc(cc.getId()))
                .thenReturn(List.of(s1, s2));

        service.onSessionCompleted(cc.getId());

        assertThat(cc.getStatus()).isEqualTo("COMPLETED");
    }

    // ── markReconciled ─────────────────────────────────────────────────────────

    @Test
    void markReconciled_setsReconciledStatus() {
        CycleCount cc = cycleCountWithStatus("UPLOADED");
        when(cycleCountRepository.findById(cc.getId())).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenReturn(cc);

        service.markReconciled(cc.getId(), OffsetDateTime.now());

        assertThat(cc.getStatus()).isEqualTo("RECONCILED");
        verify(cycleCountRepository).save(cc);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static CycleCount cycleCountWithStatus(String status) {
        return CycleCount.builder()
                .id(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .status(status)
                .countDate(LocalDate.now())
                .createdBy(UUID.randomUUID())
                .build();
    }

    private static SohSession sessionWithStatus(String status) {
        SohSession s = new SohSession();
        s.setStatus(status);
        return s;
    }

    private static CycleCountResponse mockResponse(CycleCount cc) {
        return new CycleCountResponse(cc.getId(), cc.getStoreId(), cc.getCountDate(),
                cc.getStatus(), cc.getCreatedBy(), cc.getNotes(),
                OffsetDateTime.now(), OffsetDateTime.now(), List.of());
    }
}
