package com.storelense.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpProductPage(
        @JsonProperty("data")        List<ErpProduct> data,
        @JsonProperty("next_cursor") String           nextCursor,
        @JsonProperty("total")       int              total,
        @JsonProperty("has_more")    boolean          hasMore
) {}
