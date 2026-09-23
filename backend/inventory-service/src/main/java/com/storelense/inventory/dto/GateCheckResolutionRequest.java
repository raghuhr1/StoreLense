package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record GateCheckResolutionRequest(
        @NotBlank String resolution
) {}
