package com.storelense.soh.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCycleCountRequest(
        @NotNull UUID storeId,
        LocalDate countDate,   // defaults to today if null
        String notes
) {}
