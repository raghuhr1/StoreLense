package com.storelense.common.security;

import java.security.Principal;
import java.util.UUID;

public record StoreLensePrincipal(UUID userId, String role, UUID storeId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean canAccessStore(UUID targetStoreId) {
        return isAdmin() || (storeId != null && storeId.equals(targetStoreId));
    }
}
