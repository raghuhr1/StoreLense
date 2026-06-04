package com.storelense.soh.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SohSessionCompletedEvent(UUID sessionId, UUID storeId, BigDecimal accuracyPct) {}
