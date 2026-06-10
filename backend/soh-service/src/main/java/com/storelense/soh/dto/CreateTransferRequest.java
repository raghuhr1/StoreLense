package com.storelense.soh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceStoreId,
        @NotNull UUID destStoreId,
        @NotBlank String type,
        @NotEmpty List<String> epcs
) {}
