package com.storelense.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 500) String name,
        String description,
        UUID categoryId,
        String brand,
        String supplierCode,
        String erpProductCode,
        String unitOfMeasure,
        Integer weightGrams,
        boolean rfidEnabled
) {}
