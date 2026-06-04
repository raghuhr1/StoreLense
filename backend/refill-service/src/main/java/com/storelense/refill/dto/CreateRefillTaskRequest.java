package com.storelense.refill.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateRefillTaskRequest(
        @NotNull UUID storeId,
        String taskType,
        Integer priority,
        String source,
        UUID sourceSessionId,
        LocalDate dueDate,
        String notes,
        List<TaskItemRequest> items
) {}
