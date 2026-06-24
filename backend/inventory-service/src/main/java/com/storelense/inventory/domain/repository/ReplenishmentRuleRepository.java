package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.ReplenishmentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReplenishmentRuleRepository extends JpaRepository<ReplenishmentRule, UUID> {

    List<ReplenishmentRule> findByStoreIdAndActiveTrue(UUID storeId);

    Optional<ReplenishmentRule> findByStoreIdAndTriggerStatus(UUID storeId, String triggerStatus);
}
