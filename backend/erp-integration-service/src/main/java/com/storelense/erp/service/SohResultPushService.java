package com.storelense.erp.service;

import com.storelense.common.event.SohResultOutboundEvent;
import com.storelense.erp.adapter.ErpClient;
import com.storelense.erp.domain.entity.ErpProductMapping;
import com.storelense.erp.domain.entity.ErpStoreMapping;
import com.storelense.erp.domain.entity.ErpSyncLog;
import com.storelense.erp.domain.repository.ErpProductMappingRepository;
import com.storelense.erp.domain.repository.ErpStoreMappingRepository;
import com.storelense.erp.domain.repository.ErpSyncLogRepository;
import com.storelense.erp.dto.ErpSohPushRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SohResultPushService {

    private final ErpClient                    erpClient;
    private final ErpStoreMappingRepository    storeMappingRepository;
    private final ErpProductMappingRepository  productMappingRepository;
    private final ErpSyncLogRepository         syncLogRepository;

    @Transactional
    public void pushSohResult(SohResultOutboundEvent event) {
        Optional<ErpStoreMapping> storeMapping =
                storeMappingRepository.findByInternalStoreId(event.storeId());

        if (storeMapping.isEmpty()) {
            log.warn("No ERP store mapping for store={} — skipping SOH push", event.storeId());
            return;
        }

        ErpStoreMapping store = storeMapping.get();
        if (!store.isSohPushEnabled()) {
            log.debug("SOH push disabled for ERP store={}", store.getErpStoreCode());
            return;
        }

        // Map internal variance lines → ERP product codes
        List<ErpSohPushRequest.VarianceLine> varianceLines = event.variances().stream()
                .map(v -> {
                    String erpCode = productMappingRepository
                            .findByInternalSku(v.sku())
                            .map(ErpProductMapping::getErpProductCode)
                            .orElse(v.erpProductCode());
                    return new ErpSohPushRequest.VarianceLine(
                            erpCode, v.sku(), v.countedQty(), v.expectedQty(), v.varianceQty());
                })
                .toList();

        ErpSohPushRequest request = new ErpSohPushRequest(
                store.getErpStoreCode(),
                event.sessionId().toString(),
                event.accuracyPct(),
                event.totalUnitsCounted(),
                event.totalUnitsExpected(),
                OffsetDateTime.ofInstant(event.completedAt(), ZoneOffset.UTC),
                "StoreLense",
                varianceLines
        );

        ErpSyncLog syncLog = syncLogRepository.save(ErpSyncLog.builder()
                .syncType("SOH_OUTBOUND")
                .direction("OUTBOUND")
                .status("running")
                .triggeredBy("kafka")
                .build());

        boolean success = erpClient.pushSohResult(request);

        syncLog.setStatus(success ? "completed" : "failed");
        syncLog.setRecordsFetched(1);
        syncLog.setRecordsPublished(success ? 1 : 0);
        syncLog.setRecordsFailed(success ? 0 : 1);
        syncLog.setCompletedAt(OffsetDateTime.now());
        if (!success) {
            syncLog.setErrorMessage("ERP rejected or unreachable");
        }
        syncLogRepository.save(syncLog);
    }
}
