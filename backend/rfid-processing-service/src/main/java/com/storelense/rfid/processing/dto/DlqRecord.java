package com.storelense.rfid.processing.dto;

import com.storelense.common.event.RfidReadEvent;

import java.time.OffsetDateTime;

public record DlqRecord(
        String         key,
        RfidReadEvent  event,
        String         errorMessage,
        OffsetDateTime failedAt,
        int            partition,
        long           offset
) {}
