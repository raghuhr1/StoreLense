package com.storelense.soh.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartSessionRequest(
        @NotNull UUID storeId,
        UUID   zoneId,
        String sessionType,
        String notes,
        String source,
        String zoneRegion,
        /** Optional — links this session to a parent cycle count. */
        UUID   cycleCountId,
        /** SALES_FLOOR or BACKROOM. Required when cycleCountId is set. */
        String locationCode,
        /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES. Only valid when locationCode = SALES_FLOOR. */
        String sectionCode
) {}
