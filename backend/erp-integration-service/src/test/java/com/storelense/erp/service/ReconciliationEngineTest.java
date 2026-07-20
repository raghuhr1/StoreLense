package com.storelense.erp.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.erp.domain.entity.*;
import com.storelense.erp.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationEngineTest {

    @Mock CcReconciliationRepository     reconciliationRepository;
    @Mock CcReconciliationItemRepository itemRepository;
    @Mock ErpImportBatchRepository       batchRepository;
    @Mock SohServiceClient               sohServiceClient;
    @Mock JdbcClient                     jdbcClient;

    @InjectMocks ReconciliationEngine engine;

    // ── approve ────────────────────────────────────────────────────────────────

    @Test
    void approve_setsApprovedStatus_whenPendingApproval() {
        UUID reconId   = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("PENDING_APPROVAL");
        recon.setId(reconId);

        when(reconciliationRepository.findById(reconId)).thenReturn(Optional.of(recon));
        when(reconciliationRepository.save(any())).thenReturn(recon);

        CcReconciliation result = engine.approve(reconId, reviewerId);

        assertThat(recon.getStatus()).isEqualTo("APPROVED");
        assertThat(recon.getReviewerId()).isEqualTo(reviewerId);
        assertThat(recon.getApprovedAt()).isNotNull();
        verify(reconciliationRepository).save(recon);
    }

    @Test
    void approve_setsApprovedStatus_whenCompleted() {
        UUID reconId   = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("COMPLETED");
        recon.setId(reconId);

        when(reconciliationRepository.findById(reconId)).thenReturn(Optional.of(recon));
        when(reconciliationRepository.save(any())).thenReturn(recon);

        engine.approve(reconId, UUID.randomUUID());

        assertThat(recon.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_throwsIllegalStateException_whenAlreadyApproved() {
        UUID reconId = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("APPROVED");
        recon.setId(reconId);

        when(reconciliationRepository.findById(reconId)).thenReturn(Optional.of(recon));

        assertThatThrownBy(() -> engine.approve(reconId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");

        verify(reconciliationRepository, never()).save(any());
    }

    @Test
    void approve_throwsIllegalStateException_whenRunning() {
        UUID reconId = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("RUNNING");
        recon.setId(reconId);

        when(reconciliationRepository.findById(reconId)).thenReturn(Optional.of(recon));

        assertThatThrownBy(() -> engine.approve(reconId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approve_throwsResourceNotFoundException_whenReconNotFound() {
        UUID missing = UUID.randomUUID();
        when(reconciliationRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.approve(missing, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_setsApprovedAt_toCurrentTime() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        UUID reconId = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("PENDING_APPROVAL");
        recon.setId(reconId);
        when(reconciliationRepository.findById(reconId)).thenReturn(Optional.of(recon));
        when(reconciliationRepository.save(any())).thenReturn(recon);

        engine.approve(reconId, UUID.randomUUID());

        assertThat(recon.getApprovedAt()).isAfter(before);
    }

    // ── getLatestCountResult ───────────────────────────────────────────────────

    @Test
    void getLatestCountResult_delegatesToRepository() {
        UUID cycleCountId = UUID.randomUUID();
        CcReconciliation recon = reconWithStatus("PENDING_APPROVAL");

        when(reconciliationRepository.findTopByCycleCountIdOrderByRunAtDesc(cycleCountId))
                .thenReturn(Optional.of(recon));

        CcReconciliation result = engine.getLatestCountResult(cycleCountId);
        assertThat(result).isSameAs(recon);
    }

    @Test
    void getLatestCountResult_throwsResourceNotFoundException_whenNoneFound() {
        UUID cycleCountId = UUID.randomUUID();
        when(reconciliationRepository.findTopByCycleCountIdOrderByRunAtDesc(cycleCountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.getLatestCountResult(cycleCountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── persistCountReconciliation — EPC set logic ────────────────────────────

    @Test
    void persistCountReconciliation_computesCorrectMatchMissingExtra() {
        UUID cycleCountId = UUID.randomUUID();
        UUID storeId      = UUID.randomUUID();
        UUID batchId      = UUID.randomUUID();

        // epc1 → SALES_FLOOR (scanned)
        // epc2 → BACKROOM    (scanned)
        // epc3 → not scanned (missing from both floor and back)
        // epc4 → SALES_FLOOR (extra — not in ERP snapshot)
        Map<String, String> epcToLocation = new LinkedHashMap<>();
        epcToLocation.put("epc1", "SALES_FLOOR");
        epcToLocation.put("epc2", "BACKROOM");
        epcToLocation.put("epc4", "SALES_FLOOR");  // extra

        Map<String, String> epcToSection = new LinkedHashMap<>();
        epcToSection.put("epc1", "MENS");
        epcToSection.put("epc2", null);
        epcToSection.put("epc4", "MENS");

        // ERP snapshot contains epc1, epc2, epc3
        Map<String, String> epcToEan = new LinkedHashMap<>();
        epcToEan.put("epc1", "EAN123");
        epcToEan.put("epc2", "EAN123");
        epcToEan.put("epc3", "EAN123");

        ErpImportBatch batch = new ErpImportBatch();
        batch.setId(batchId);

        CcReconciliation savedRecon = reconWithStatus("PENDING_APPROVAL");
        savedRecon.setId(UUID.randomUUID());
        when(reconciliationRepository.save(any())).thenReturn(savedRecon);
        when(itemRepository.saveAll(any())).thenReturn(List.of());

        engine.persistCountReconciliation(cycleCountId, storeId, batch, epcToLocation, epcToSection);

        ArgumentCaptor<CcReconciliation> reconCaptor = ArgumentCaptor.forClass(CcReconciliation.class);
        verify(reconciliationRepository).save(reconCaptor.capture());
        CcReconciliation captured = reconCaptor.getValue();

        assertThat(captured.getTotalExpected()).isEqualTo(3);  // epc1, epc2, epc3
        assertThat(captured.getTotalScanned()).isEqualTo(3);   // epc1, epc2, epc4
        assertThat(captured.getMatchedCount()).isEqualTo(2);   // epc1, epc2
        assertThat(captured.getMissingCount()).isEqualTo(1);   // epc3
        assertThat(captured.getExtraCount()).isEqualTo(1);     // epc4
    }

    @Test
    void persistCountReconciliation_computesFloorBackroomBreakdown() {
        UUID cycleCountId = UUID.randomUUID();
        UUID storeId      = UUID.randomUUID();
        ErpImportBatch batch = new ErpImportBatch();
        batch.setId(UUID.randomUUID());

        // floor: epc1 scanned, epc3 missing
        // back:  epc2 scanned, epc4 missing
        Map<String, String> epcToLocation = Map.of(
                "epc1", "SALES_FLOOR",
                "epc2", "BACKROOM"
        );
        Map<String, String> epcToSection = Map.of("epc1", "WOMENS");

        Map<String, String> epcToEan = new LinkedHashMap<>();
        epcToEan.put("epc1", "EAN999");
        epcToEan.put("epc2", "EAN999");
        epcToEan.put("epc3", "EAN999");  // missing — not scanned on floor
        epcToEan.put("epc4", "EAN999");  // missing — not scanned on backroom

        CcReconciliation saved = reconWithStatus("PENDING_APPROVAL");
        saved.setId(UUID.randomUUID());
        when(reconciliationRepository.save(any())).thenReturn(saved);
        when(itemRepository.saveAll(any())).thenReturn(List.of());

        engine.persistCountReconciliation(cycleCountId, storeId, batch, epcToLocation, epcToSection);

        ArgumentCaptor<CcReconciliation> captor = ArgumentCaptor.forClass(CcReconciliation.class);
        verify(reconciliationRepository).save(captor.capture());
        CcReconciliation r = captor.getValue();

        assertThat(r.getFloorScanned()).isEqualTo(1);     // epc1
        assertThat(r.getBackroomScanned()).isEqualTo(1);  // epc2
        // floorMissing = missing.count(not in floorScannedSet)
        // missing = {epc3, epc4}; floor had epc1 but not epc3/epc4 → floorMissing = 2
        assertThat(r.getFloorMissing()).isEqualTo(2);
        assertThat(r.getBackroomMissing()).isEqualTo(2);
    }

    @Test
    void persistCountReconciliation_setsPendingApprovalStatus() {
        ErpImportBatch batch = new ErpImportBatch();
        batch.setId(UUID.randomUUID());

        Map<String, String> epcToLocation = Map.of("epc1", "SALES_FLOOR");
        Map<String, String> epcToSection  = Map.of();
        Map<String, String> epcToEan      = Map.of("epc1", "EAN");

        CcReconciliation saved = reconWithStatus("PENDING_APPROVAL");
        saved.setId(UUID.randomUUID());
        when(reconciliationRepository.save(any())).thenReturn(saved);
        when(itemRepository.saveAll(any())).thenReturn(List.of());

        engine.persistCountReconciliation(UUID.randomUUID(), UUID.randomUUID(), batch, epcToLocation, epcToSection);

        ArgumentCaptor<CcReconciliation> captor = ArgumentCaptor.forClass(CcReconciliation.class);
        verify(reconciliationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void persistCountReconciliation_setsHundredPercentAccuracy_whenNothingMissing() {
        ErpImportBatch batch = new ErpImportBatch();
        batch.setId(UUID.randomUUID());

        Map<String, String> epcToLocation = Map.of("epc1", "SALES_FLOOR", "epc2", "BACKROOM");
        Map<String, String> epcToSection  = Map.of();
        Map<String, String> epcToEan      = Map.of("epc1", "EAN", "epc2", "EAN");

        CcReconciliation saved = reconWithStatus("PENDING_APPROVAL");
        saved.setId(UUID.randomUUID());
        when(reconciliationRepository.save(any())).thenReturn(saved);
        when(itemRepository.saveAll(any())).thenReturn(List.of());

        engine.persistCountReconciliation(UUID.randomUUID(), UUID.randomUUID(), batch, epcToLocation, epcToSection);

        ArgumentCaptor<CcReconciliation> captor = ArgumentCaptor.forClass(CcReconciliation.class);
        verify(reconciliationRepository).save(captor.capture());
        assertThat(captor.getValue().getAccuracyPct())
                .isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(captor.getValue().getMissingCount()).isEqualTo(0);
    }

    @Test
    void persistCountReconciliation_savesItemsWithCorrectLocationAndStatus() {
        ErpImportBatch batch = new ErpImportBatch();
        batch.setId(UUID.randomUUID());

        Map<String, String> epcToLocation = Map.of("epc1", "SALES_FLOOR");
        Map<String, String> epcToSection  = Map.of("epc1", "KIDS");
        // epc1 is both expected and scanned → MATCH
        Map<String, String> epcToEan = Map.of("epc1", "EAN-KIDS");

        CcReconciliation saved = reconWithStatus("PENDING_APPROVAL");
        saved.setId(UUID.randomUUID());
        when(reconciliationRepository.save(any())).thenReturn(saved);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CcReconciliationItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        when(itemRepository.saveAll(itemsCaptor.capture())).thenReturn(List.of());

        engine.persistCountReconciliation(UUID.randomUUID(), UUID.randomUUID(), batch, epcToLocation, epcToSection);

        List<CcReconciliationItem> items = itemsCaptor.getValue();
        assertThat(items).hasSize(1);
        CcReconciliationItem matchItem = items.get(0);
        assertThat(matchItem.getEpc()).isEqualTo("epc1");
        assertThat(matchItem.getStatus()).isEqualTo("MATCH");
        assertThat(matchItem.getLocationCode()).isEqualTo("SALES_FLOOR");
        assertThat(matchItem.getSectionCode()).isEqualTo("KIDS");
    }

    // ── getItemsByLocation ─────────────────────────────────────────────────────

    @Test
    void getItemsByLocation_filtersCorrectly() {
        UUID reconId = UUID.randomUUID();
        CcReconciliationItem floorItem = new CcReconciliationItem();
        floorItem.setLocationCode("SALES_FLOOR");
        CcReconciliationItem backItem = new CcReconciliationItem();
        backItem.setLocationCode("BACKROOM");

        when(itemRepository.findByReconciliation_Id(reconId))
                .thenReturn(List.of(floorItem, backItem));

        List<CcReconciliationItem> floorOnly = engine.getItemsByLocation(reconId, "SALES_FLOOR");
        assertThat(floorOnly).containsExactly(floorItem);

        List<CcReconciliationItem> all = engine.getItemsByLocation(reconId, null);
        assertThat(all).containsExactlyInAnyOrder(floorItem, backItem);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static CcReconciliation reconWithStatus(String status) {
        CcReconciliation r = new CcReconciliation();
        r.setStatus(status);
        r.setStoreId(UUID.randomUUID());
        return r;
    }

}
