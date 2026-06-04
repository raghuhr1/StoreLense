package com.storelense.refill.dto;

import java.util.UUID;

public record TaskItemResponse(UUID id, UUID productId, UUID zoneId, int requestedQuantity, int fulfilledQuantity, String status) {}
