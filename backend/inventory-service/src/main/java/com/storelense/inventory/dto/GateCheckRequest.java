package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record GateCheckRequest(
        @NotNull  UUID         storeId,
        UUID                   guardUserId,
        String                 billRef,
        int                    expectedCount,
        int                    matchedCount,
        int                    extraCount,
        @NotBlank String       outcome,
        List<String>           epcsMatched,
        List<String>           epcsExtra
) {}
