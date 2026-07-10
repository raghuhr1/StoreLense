package com.storelense.store.domain.entity;

import java.io.Serializable;
import java.util.UUID;

public record StoreFeatureId(UUID storeId, String feature) implements Serializable {}
