package com.storelense.rfid.processing.dto;

import java.util.UUID;

public record ProcessingResult(
        String  epc,
        UUID    productId,
        UUID    zoneId,
        boolean sohEventPublished,
        boolean deduped,
        long    processingNanos
) {}
