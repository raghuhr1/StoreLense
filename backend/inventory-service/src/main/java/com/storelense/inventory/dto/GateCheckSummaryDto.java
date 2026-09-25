package com.storelense.inventory.dto;

public record GateCheckSummaryDto(
        int totalChecks,
        int released,
        int flagged,
        int abandoned,
        int totalExtraItems,
        double flagRate,
        /** FLAGGED rows still awaiting a resolution — the number that should
         *  keep nagging a guard/manager until it hits zero. */
        int unresolved,
        /** FLAGGED rows a guard/manager has already reviewed and closed out. */
        int resolved
) {}
