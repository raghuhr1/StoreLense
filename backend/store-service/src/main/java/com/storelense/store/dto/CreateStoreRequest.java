package com.storelense.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank @Size(max = 20) String storeCode,
        @NotBlank @Size(max = 255) String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        @Pattern(regexp = "[A-Z]{2}") String countryCode,
        String timezone,
        String erpStoreCode
) {}
