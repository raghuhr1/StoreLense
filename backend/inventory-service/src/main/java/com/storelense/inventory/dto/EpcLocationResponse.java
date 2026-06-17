package com.storelense.inventory.dto;

import java.util.UUID;

public record EpcLocationResponse(
        String epc,
        String zone,
        String lastSeenAt,
        UUID   storeId
) {}
