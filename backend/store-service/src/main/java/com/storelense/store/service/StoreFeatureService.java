package com.storelense.store.service;

import com.storelense.store.domain.entity.StoreFeature;
import com.storelense.store.domain.repository.StoreFeatureRepository;
import com.storelense.store.domain.repository.StoreRepository;
import com.storelense.store.dto.StoreFeatureResponse;
import com.storelense.store.dto.UpdateFeaturesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreFeatureService {

    static final Set<String> ALL_FEATURES = Set.of(
            // Web portal modules
            "INVENTORY", "INBOUND", "REPLENISHMENT", "CYCLE_COUNT",
            "TRANSFERS", "ANALYTICS", "SALES", "DEVICES", "ERP_INTEGRATION",
            // Guard (C66) app feature flags
            "GATE_CAMERA_SCANNER", "GATE_RFID_VERIFY", "GATE_STRICT_MODE", "GATE_MANUAL_ENTRY"
    );

    // Guard flags are OFF by default (manual entry is the safe fallback)
    private static final Set<String> DEFAULT_OFF_FEATURES = Set.of(
            "GATE_CAMERA_SCANNER", "GATE_RFID_VERIFY", "GATE_STRICT_MODE"
    );

    private static final String REDIS_KEY = "store:features:";

    private final StoreFeatureRepository featureRepository;
    private final StoreRepository        storeRepository;
    private final StringRedisTemplate    redisTemplate;

    @Transactional(readOnly = true)
    public List<StoreFeatureResponse> getFeatures(UUID storeId) {
        Map<String, Boolean> saved = featureRepository.findByStoreId(storeId).stream()
                .collect(Collectors.toMap(StoreFeature::getFeature, StoreFeature::isEnabled));

        return ALL_FEATURES.stream()
                .sorted()
                .map(f -> new StoreFeatureResponse(f, saved.getOrDefault(f, true)))
                .toList();
    }

    @Transactional
    public List<StoreFeatureResponse> updateFeatures(UUID storeId, UpdateFeaturesRequest req) {
        if (!storeRepository.existsById(storeId)) {
            throw new com.storelense.common.exception.ResourceNotFoundException("Store", storeId);
        }

        featureRepository.deleteByStoreId(storeId);

        List<StoreFeature> toSave = req.features().entrySet().stream()
                .filter(e -> ALL_FEATURES.contains(e.getKey()))
                .map(e -> StoreFeature.builder()
                        .storeId(storeId)
                        .feature(e.getKey())
                        .enabled(e.getValue())
                        .build())
                .toList();
        featureRepository.saveAll(toSave);
        featureRepository.flush();

        // Publish enabled features to Redis so other services can gate API calls
        String redisKey = REDIS_KEY + storeId;
        redisTemplate.delete(redisKey);
        Set<String> enabled = toSave.stream()
                .filter(StoreFeature::isEnabled)
                .map(StoreFeature::getFeature)
                .collect(Collectors.toSet());
        if (!enabled.isEmpty()) {
            redisTemplate.opsForSet().add(redisKey, enabled.toArray(new String[0]));
        }
        log.info("Store {} features updated: {}", storeId, enabled);

        return getFeatures(storeId);
    }

    /** Called when a new store is created — seeds web modules ON, guard flags to their defaults. */
    @Transactional
    public void seedDefaults(UUID storeId) {
        List<StoreFeature> defaults = ALL_FEATURES.stream()
                .map(f -> StoreFeature.builder()
                        .storeId(storeId)
                        .feature(f)
                        .enabled(!DEFAULT_OFF_FEATURES.contains(f))
                        .build())
                .toList();
        featureRepository.saveAll(defaults);

        String redisKey = REDIS_KEY + storeId;
        Set<String> enabledByDefault = ALL_FEATURES.stream()
                .filter(f -> !DEFAULT_OFF_FEATURES.contains(f))
                .collect(Collectors.toSet());
        redisTemplate.opsForSet().add(redisKey, enabledByDefault.toArray(new String[0]));
    }
}
