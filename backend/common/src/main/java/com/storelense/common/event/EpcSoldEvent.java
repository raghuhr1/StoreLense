package com.storelense.common.event;

import java.util.UUID;

/** One product's worth of EPCs marked sold in a single gate-exit/POS confirmation batch. */
public record EpcSoldEvent(UUID storeId, UUID productId, int soldQty) {}
