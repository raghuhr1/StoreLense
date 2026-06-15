package com.storelense.soh.dto;

public record MarkDoneResponse(
        String status,       // "done"
        boolean isLastActive,
        int activeCount
) {}
