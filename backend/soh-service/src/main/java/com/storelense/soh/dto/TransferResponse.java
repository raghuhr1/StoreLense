package com.storelense.soh.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID            id,
        UUID            sourceStoreId,
        UUID            destStoreId,
        String          type,
        String          status,
        UUID            createdBy,
        List<String>    epcs,
        OffsetDateTime  createdAt,
        OffsetDateTime  updatedAt,
        OffsetDateTime  receivedAt,
        List<String>    receivedEpcs
) {}
