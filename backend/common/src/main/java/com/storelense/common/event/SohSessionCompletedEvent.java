package com.storelense.common.event;

import java.math.BigDecimal;
import java.util.UUID;

public record SohSessionCompletedEvent(UUID sessionId, UUID storeId, BigDecimal accuracyPct) {}
