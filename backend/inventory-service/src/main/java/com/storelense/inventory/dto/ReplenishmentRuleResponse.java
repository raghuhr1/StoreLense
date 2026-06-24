package com.storelense.inventory.dto;

import com.storelense.inventory.domain.entity.ReplenishmentRule;

import java.util.UUID;

public record ReplenishmentRuleResponse(
        UUID    id,
        UUID    storeId,
        String  triggerStatus,
        short   priority,
        boolean active,
        String  createdAt,
        String  updatedAt
) {
    public static ReplenishmentRuleResponse from(ReplenishmentRule e) {
        return new ReplenishmentRuleResponse(
                e.getId(), e.getStoreId(), e.getTriggerStatus(), e.getPriority(),
                e.isActive(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null
        );
    }
}
