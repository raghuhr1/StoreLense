package com.storelense.common.audit;

import java.util.UUID;

public record AuditContext(UUID userId, UUID storeId, String correlationId, String ipAddress) {

    private static final ThreadLocal<AuditContext> HOLDER = new ThreadLocal<>();

    public static void set(AuditContext ctx) { HOLDER.set(ctx); }
    public static AuditContext get()         { return HOLDER.get(); }
    public static void clear()               { HOLDER.remove(); }
}
