package com.storelense.store.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoreResponse(
        UUID   id,
        String storeCode,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        String countryCode,
        String timezone,
        boolean active,
        String erpStoreCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
