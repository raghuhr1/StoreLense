package com.storelense.common.feature;

import java.lang.annotation.*;

/**
 * Gates an API endpoint behind a store-level feature flag.
 * Place on a controller method; the aspect checks Redis for the store's enabled features.
 * ADMIN role bypasses the check (admins can always access everything).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFeature {
    String value();
}
