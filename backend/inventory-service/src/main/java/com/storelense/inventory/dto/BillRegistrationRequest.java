package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BillRegistrationRequest(
        @NotNull  UUID         storeId,
        @NotBlank String       billRef,
        UUID                   cashierId,
        @NotEmpty List<BillItemDto> items
) {}
