package com.storelense.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpInventoryPage(
        @JsonProperty("data")        List<ErpInventoryItem> data,
        @JsonProperty("next_cursor") String                 nextCursor,
        @JsonProperty("has_more")    boolean                hasMore
) {}
