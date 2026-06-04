package com.storelense.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Inbound product record from ERP REST API — field names match ERP schema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpProduct(
        @JsonProperty("product_code")   String  productCode,
        @JsonProperty("sku")            String  sku,
        @JsonProperty("product_name")   String  name,
        @JsonProperty("category_code")  String  categoryCode,
        @JsonProperty("brand")          String  brand,
        @JsonProperty("uom")            String  unitOfMeasure,
        @JsonProperty("weight_grams")   Integer weightGrams,
        @JsonProperty("rfid_enabled")   boolean rfidEnabled,
        @JsonProperty("active")         boolean active,
        @JsonProperty("checksum")       String  checksum
) {}
