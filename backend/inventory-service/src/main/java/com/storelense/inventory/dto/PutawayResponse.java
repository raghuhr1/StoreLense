package com.storelense.inventory.dto;

public record PutawayResponse(
        int movedCount,
        int skippedCount,
        String message
) {}
