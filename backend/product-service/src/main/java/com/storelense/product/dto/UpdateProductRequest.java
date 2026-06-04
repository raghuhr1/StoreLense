package com.storelense.product.dto;

import java.util.UUID;

public record UpdateProductRequest(
        String name, String description, UUID categoryId,
        String brand, String supplierCode, String erpProductCode,
        String unitOfMeasure, Integer weightGrams,
        Boolean rfidEnabled, Boolean active
) {}
