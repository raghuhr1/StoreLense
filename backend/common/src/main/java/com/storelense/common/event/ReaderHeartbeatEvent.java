package com.storelense.common.event;

import java.time.Instant;
import java.util.UUID;

public record ReaderHeartbeatEvent(
        UUID    readerId,
        UUID    storeId,
        String  readerCode,
        String  firmwareVersion,
        boolean online,
        Instant timestamp
) {}
