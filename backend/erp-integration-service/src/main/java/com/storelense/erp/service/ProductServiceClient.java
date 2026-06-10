package com.storelense.erp.service;

import com.storelense.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class ProductServiceClient {

    private final RestClient productRestClient;

    public ProductServiceClient(@Qualifier("productRestClient") RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    /**
     * Returns all active EPC hex strings associated with the given EAN barcode.
     * Returns an empty list on any error so that callers can mark the snapshot UNRESOLVED.
     */
    public List<String> getEpcsByEan(String ean) {
        try {
            ApiResponse<List<String>> response = productRestClient.get()
                    .uri("/api/products/by-ean/{ean}/epcs", ean)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Product-service EPC lookup failed for EAN {}: {}", ean, e.getMessage());
            return List.of();
        }
    }
}
