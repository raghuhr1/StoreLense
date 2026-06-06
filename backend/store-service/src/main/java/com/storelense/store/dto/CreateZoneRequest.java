package com.storelense.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateZoneRequest(
        @NotBlank @Size(max = 50) String zoneCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank
        @Pattern(regexp = "^(floor|backroom|fitting_room|stockroom|display|entrance)$",
                 message = "zoneType must be one of: floor, backroom, fitting_room, stockroom, display, entrance")
        String zoneType,
        Integer displayOrder
) {}
