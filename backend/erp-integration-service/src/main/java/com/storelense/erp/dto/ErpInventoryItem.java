package com.storelense.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpInventoryItem(
        @JsonProperty("store_code")    String  storeCode,
        @JsonProperty("product_code")  String  productCode,
        @JsonProperty("sku")           String  sku,
        @JsonProperty("qty_expected")  int     expectedQuantity,
        @JsonProperty("valid_date")    String  validDate
) {}
