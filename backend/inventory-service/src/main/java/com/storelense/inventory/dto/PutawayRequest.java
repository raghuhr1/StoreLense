package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PutawayRequest(
        @NotNull  UUID storeId,
        @NotNull  UUID zoneId,
        @NotEmpty List<String> epcs
) {}
