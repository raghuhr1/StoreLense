package com.storelense.inventory.dto;

import java.util.UUID;

public record ReceiveShipmentResponse(
        UUID shipmentId,
        int epcsReceived,
        String status
) {}
