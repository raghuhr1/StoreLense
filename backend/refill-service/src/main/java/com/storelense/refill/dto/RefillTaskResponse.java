package com.storelense.refill.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RefillTaskResponse(
        UUID id, UUID storeId, String taskType, String status,
        short priority, String source, UUID sourceSessionId,
        LocalDate dueDate, String notes, UUID createdBy,
        OffsetDateTime createdAt, OffsetDateTime completedAt,
        List<TaskItemResponse> items
) {}
