package com.storelense.store.dto;

public record UpdateStoreRequest(
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        String timezone,
        String erpStoreCode,
        Boolean active
) {}
