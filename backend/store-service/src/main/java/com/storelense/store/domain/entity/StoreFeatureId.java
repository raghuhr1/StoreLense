package com.storelense.store.domain.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class StoreFeatureId implements Serializable {

    private UUID   storeId;
    private String feature;

    public StoreFeatureId() {}

    public StoreFeatureId(UUID storeId, String feature) {
        this.storeId = storeId;
        this.feature = feature;
    }

    public UUID   getStoreId() { return storeId; }
    public String getFeature() { return feature; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoreFeatureId that)) return false;
        return Objects.equals(storeId, that.storeId) && Objects.equals(feature, that.feature);
    }

    @Override public int hashCode() { return Objects.hash(storeId, feature); }
}
