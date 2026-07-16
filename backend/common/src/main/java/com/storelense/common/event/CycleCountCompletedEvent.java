package com.storelense.common.event;

import java.util.UUID;

/** All sessions under a cycle count reached 'completed' — triggers combined reconciliation. */
public record CycleCountCompletedEvent(UUID cycleCountId, UUID storeId) {}
