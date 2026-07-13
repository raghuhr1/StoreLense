package com.storelense.inventory.dto;

public record GateCheckSummaryDto(
        int totalChecks,
        int released,
        int flagged,
        int abandoned,
        int totalExtraItems,
        double flagRate
) {}
