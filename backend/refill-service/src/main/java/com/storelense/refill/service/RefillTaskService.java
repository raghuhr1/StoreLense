package com.storelense.refill.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.RefillTaskCreatedEvent;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.refill.domain.entity.RefillAssignment;
import com.storelense.refill.domain.entity.RefillTask;
import com.storelense.refill.domain.entity.RefillTaskItem;
import com.storelense.refill.domain.repository.RefillTaskRepository;
import com.storelense.refill.dto.*;
import com.storelense.refill.mapper.RefillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefillTaskService {

    private final RefillTaskRepository taskRepository;
    private final RefillMapper         mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public PageResponse<RefillTaskResponse> listTasks(UUID storeId, String status, Pageable pageable) {
        var page = status != null
                ? taskRepository.findByStoreIdAndStatusOrderByPriorityAscCreatedAtDesc(storeId, status, pageable)
                : taskRepository.findByStoreIdOrderByPriorityAscCreatedAtDesc(storeId, pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RefillTaskResponse getTask(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public RefillTaskResponse createTask(CreateRefillTaskRequest req, UUID createdBy) {
        RefillTask task = RefillTask.builder()
                .storeId(req.storeId())
                .taskType(req.taskType() != null ? req.taskType() : "replenishment")
                .priority((short)(req.priority() != null ? req.priority() : 5))
                .source(req.source() != null ? req.source() : "manual")
                .sourceSessionId(req.sourceSessionId())
                .dueDate(req.dueDate())
                .notes(req.notes())
                .createdBy(createdBy)
                .build();

        if (req.items() != null) {
            req.items().forEach(item ->
                    task.getItems().add(RefillTaskItem.builder()
                            .task(task)
                            .productId(item.productId())
                            .zoneId(item.zoneId())
                            .requestedQuantity(item.requestedQuantity())
                            .build())
            );
        }

        RefillTask saved = taskRepository.save(task);

        kafkaTemplate.send("refill.task.created", saved.getId().toString(),
                new RefillTaskCreatedEvent(
                        UUID.randomUUID().toString(), saved.getId(), saved.getStoreId(),
                        saved.getSource(), saved.getSourceSessionId(), saved.getCreatedAt().toInstant()));

        return mapper.toResponse(saved);
    }

    @Transactional
    public RefillTaskResponse assignTask(UUID taskId, UUID assignedTo, UUID assignedBy) {
        RefillTask task = findOrThrow(taskId);
        if (!task.isAssignable()) {
            throw new BusinessException("TASK_NOT_ASSIGNABLE",
                    "Task with status '" + task.getStatus() + "' cannot be assigned");
        }

        RefillAssignment assignment = RefillAssignment.builder()
                .task(task)
                .assignedTo(assignedTo)
                .assignedBy(assignedBy)
                .build();

        task.setAssignment(assignment);
        task.setStatus("assigned");
        return mapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public RefillTaskResponse updateItemFulfilment(UUID taskId, UUID itemId, int fulfilledQty) {
        RefillTask task = findOrThrow(taskId);
        RefillTaskItem item = task.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("RefillTaskItem", itemId));

        item.setFulfilledQuantity(fulfilledQty);
        item.setStatus(fulfilledQty >= item.getRequestedQuantity() ? "fulfilled" : "partial");

        boolean allDone = task.getItems().stream()
                .allMatch(i -> "fulfilled".equals(i.getStatus()) || "skipped".equals(i.getStatus()));

        if (allDone) {
            task.setStatus("completed");
            task.setCompletedAt(OffsetDateTime.now());
            if (task.getAssignment() != null) {
                task.getAssignment().setCompletedAt(OffsetDateTime.now());
                task.getAssignment().setStatus("completed");
            }
        } else if ("pending".equals(task.getStatus())) {
            task.setStatus("in_progress");
        }

        return mapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public void cancelTask(UUID taskId, String reason) {
        RefillTask task = findOrThrow(taskId);
        if ("completed".equals(task.getStatus())) {
            throw new BusinessException("TASK_COMPLETED", "Cannot cancel a completed task", HttpStatus.CONFLICT);
        }
        task.setStatus("cancelled");
        task.setCancelledAt(OffsetDateTime.now());
        task.setCancellationReason(reason);
        taskRepository.save(task);
    }

    private RefillTask findOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RefillTask", id));
    }
}
