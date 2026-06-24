package com.storelense.erp.service;

import com.storelense.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SohServiceClient {

    /** Minimal session fields needed by the reconciliation engine. */
    public record SohSessionInfo(UUID id, UUID storeId, String zoneRegion, String status) {}

    private final RestClient sohRestClient;

    public SohServiceClient(@Qualifier("sohRestClient") RestClient sohRestClient) {
        this.sohRestClient = sohRestClient;
    }

    public SohSessionInfo getSession(UUID sessionId) {
        try {
            ApiResponse<Map<String, Object>> response = sohRestClient.get()
                    .uri("/api/soh/sessions/{id}", sessionId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                throw new IllegalStateException("Session " + sessionId + " not found in soh-service");
            }
            Map<String, Object> d = response.getData();
            return new SohSessionInfo(
                    UUID.fromString((String) d.get("id")),
                    UUID.fromString((String) d.get("storeId")),
                    (String) d.get("zoneRegion"),
                    (String) d.get("status")
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Could not reach soh-service for session {}: {}. " +
                      "Check SOH_SERVICE_TOKEN and SOH_SERVICE_URL configuration.", sessionId, e.getMessage());
            throw new IllegalStateException(
                    "soh-service unreachable or returned an error for session " + sessionId +
                    ". Ensure SOH_SERVICE_TOKEN is configured. Cause: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the list of scanned EPC strings for a completed SOH session.
     * Returns an empty list when the soh-service is unreachable rather than
     * propagating the exception, so callers can degrade gracefully.
     */
    public List<String> getSessionEpcs(UUID sessionId) {
        try {
            ApiResponse<List<String>> response = sohRestClient.get()
                    .uri("/api/soh/sessions/{id}/epcs", sessionId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return response != null && response.getData() != null ? response.getData() : List.of();
        } catch (Exception e) {
            log.warn("EPC fetch failed for session {}: {}", sessionId, e.getMessage());
            return List.of();
        }
    }
}
