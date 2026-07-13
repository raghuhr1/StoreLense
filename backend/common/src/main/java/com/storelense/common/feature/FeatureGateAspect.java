package com.storelense.common.feature;

import com.storelense.common.exception.BusinessException;
import com.storelense.common.security.StoreLensePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class FeatureGateAspect {

    private static final String REDIS_KEY = "store:features:";

    private final StringRedisTemplate redisTemplate;

    @Before("@annotation(requiresFeature)")
    public void checkFeature(RequiresFeature requiresFeature) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof StoreLensePrincipal principal)) return;

        // ADMIN role bypasses all feature gates
        if (principal.isAdmin()) return;

        String storeId = principal.storeId() != null ? principal.storeId().toString() : null;
        if (storeId == null) return;

        String feature = requiresFeature.value();
        String key = REDIS_KEY + storeId;

        Boolean enabled = redisTemplate.opsForSet().isMember(key, feature);

        // If Redis has no entry for this store (cache miss or new store), allow access
        // Store-service seeds Redis on every feature update and store creation
        Long keySize = redisTemplate.opsForSet().size(key);
        if (keySize == null || keySize == 0) {
            log.debug("Feature cache miss for store {} — allowing access", storeId);
            return;
        }

        if (!Boolean.TRUE.equals(enabled)) {
            throw new BusinessException("FEATURE_DISABLED",
                    "The feature '" + feature + "' is not enabled for this store",
                    HttpStatus.FORBIDDEN);
        }
    }
}
