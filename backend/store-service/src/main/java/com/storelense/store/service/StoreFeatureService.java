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
            "INVENTORY", "INBOUND", "REPLENISHMENT", "CYCLE_COUNT",
            "TRANSFERS", "ANALYTICS", "SALES", "DEVICES", "ERP_INTEGRATION"
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

    /** Called when a new store is created — seeds all features ON. */
    @Transactional
    public void seedDefaults(UUID storeId) {
        List<StoreFeature> defaults = ALL_FEATURES.stream()
                .map(f -> StoreFeature.builder().storeId(storeId).feature(f).enabled(true).build())
                .toList();
        featureRepository.saveAll(defaults);

        String redisKey = REDIS_KEY + storeId;
        redisTemplate.opsForSet().add(redisKey, ALL_FEATURES.toArray(new String[0]));
    }
}
