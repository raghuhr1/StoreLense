package com.storelense.inventory.dto;

import com.storelense.inventory.domain.entity.ZoneParLevel;

import java.util.UUID;

public record ZoneParLevelResponse(
        UUID   id,
        UUID   storeId,
        UUID   zoneId,
        UUID   productId,
        int    parQty,
        int    minQty,
        boolean active,
        String  createdAt,
        String  updatedAt
) {
    public static ZoneParLevelResponse from(ZoneParLevel e) {
        return new ZoneParLevelResponse(
                e.getId(), e.getStoreId(), e.getZoneId(), e.getProductId(),
                e.getParQty(), e.getMinQty(), e.isActive(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null
        );
    }
}
