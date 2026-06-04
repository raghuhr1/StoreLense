package com.storelense.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Outbound SOH result payload pushed to ERP */
public record ErpSohPushRequest(
        @JsonProperty("store_code")          String            storeCode,
        @JsonProperty("session_id")          String            sessionId,
        @JsonProperty("accuracy_pct")        BigDecimal        accuracyPct,
        @JsonProperty("total_counted")       int               totalCounted,
        @JsonProperty("total_expected")      int               totalExpected,
        @JsonProperty("completed_at")        OffsetDateTime    completedAt,
        @JsonProperty("source_system")       String            sourceSystem,
        @JsonProperty("variances")           List<VarianceLine> variances
) {
    public record VarianceLine(
            @JsonProperty("product_code") String productCode,
            @JsonProperty("sku")          String sku,
            @JsonProperty("counted")      int    counted,
            @JsonProperty("expected")     int    expected,
            @JsonProperty("variance")     int    variance
    ) {}
}
