package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MarkEpcsSoldRequest(
        @NotNull UUID storeId,
        @NotEmpty List<String> epcs
) {}
