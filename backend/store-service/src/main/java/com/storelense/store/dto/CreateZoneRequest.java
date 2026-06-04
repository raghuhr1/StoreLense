package com.storelense.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateZoneRequest(
        @NotBlank @Size(max = 50) String zoneCode,
        @NotBlank @Size(max = 255) String name,
        String zoneType,
        Integer displayOrder
) {}
