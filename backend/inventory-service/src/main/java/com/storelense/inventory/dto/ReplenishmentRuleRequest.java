package com.storelense.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ReplenishmentRuleRequest(
        @NotNull UUID storeId,
        @NotBlank @Pattern(regexp = "low|critical") String triggerStatus,
        @Min(1) @Max(10) short priority
) {}
