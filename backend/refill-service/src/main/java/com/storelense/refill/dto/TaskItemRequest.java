package com.storelense.refill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TaskItemRequest(@NotNull UUID productId, UUID zoneId, @Positive int requestedQuantity) {}
