package com.storelense.erp.adapter;

import com.storelense.erp.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestErpClient implements ErpClient {

    private final RestClient erpRestClient;

    @Override
    @Retry(name = "erp")
    @CircuitBreaker(name = "erp", fallbackMethod = "fetchProductsFallback")
    public ErpProductPage fetchProducts(String cursor, int pageSize) {
        String uri = "/products?page_size=" + pageSize
                + (cursor != null ? "&cursor=" + cursor : "");

        log.debug("Fetching ERP products cursor={} pageSize={}", cursor, pageSize);
        return erpRestClient.get()
                .uri(uri)
                .retrieve()
                .body(ErpProductPage.class);
    }

    @SuppressWarnings("unused")
    private ErpProductPage fetchProductsFallback(String cursor, int pageSize, Exception ex) {
        log.warn("ERP product fetch fallback triggered (circuit open or retries exhausted): {}", ex.getMessage());
        return new ErpProductPage(java.util.List.of(), null, 0, false);
    }

    @Override
    @Retry(name = "erp")
    @CircuitBreaker(name = "erp", fallbackMethod = "fetchInventoryFallback")
    public ErpInventoryPage fetchExpectedInventory(String erpStoreCode, String cursor, int pageSize) {
        String uri = "/inventory/expected?store_code=" + erpStoreCode
                + "&page_size=" + pageSize
                + (cursor != null ? "&cursor=" + cursor : "");

        log.debug("Fetching ERP inventory storeCode={} cursor={}", erpStoreCode, cursor);
        return erpRestClient.get()
                .uri(uri)
                .retrieve()
                .body(ErpInventoryPage.class);
    }

    @SuppressWarnings("unused")
    private ErpInventoryPage fetchInventoryFallback(String storeCode, String cursor,
                                                     int pageSize, Exception ex) {
        log.warn("ERP inventory fetch fallback: {}", ex.getMessage());
        return new ErpInventoryPage(java.util.List.of(), null, false);
    }

    @Override
    @Retry(name = "erp")
    @CircuitBreaker(name = "erp")
    public boolean pushSohResult(ErpSohPushRequest request) {
        try {
            erpRestClient.post()
                    .uri("/inventory/soh-result")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("SOH result pushed to ERP for store={} session={}",
                    request.storeCode(), request.sessionId());
            return true;
        } catch (RestClientException ex) {
            log.error("Failed to push SOH result to ERP: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean isReachable() {
        try {
            erpRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("ERP health check failed: {}", e.getMessage());
            return false;
        }
    }
}
