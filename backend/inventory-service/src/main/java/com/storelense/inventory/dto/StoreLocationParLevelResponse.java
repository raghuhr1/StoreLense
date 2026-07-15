package com.storelense.inventory.dto;

import com.storelense.inventory.domain.entity.StoreLocationParLevel;

import java.util.UUID;

public record StoreLocationParLevelResponse(
        UUID    id,
        UUID    storeId,
        String  locationCode,
        UUID    productId,
        int     parQty,
        int     minQty,
        boolean active,
        String  createdAt,
        String  updatedAt
) {
    public static StoreLocationParLevelResponse from(StoreLocationParLevel e) {
        return new StoreLocationParLevelResponse(
                e.getId(), e.getStoreId(), e.getLocationCode(), e.getProductId(),
                e.getParQty(), e.getMinQty(), e.isActive(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null
        );
    }
}
