package com.storelense.soh.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinSessionRequest(
        @NotBlank String deviceId,
        String zoneRegion
) {}
