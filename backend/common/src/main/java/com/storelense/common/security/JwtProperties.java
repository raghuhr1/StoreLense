package com.storelense.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storelense.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiryMs,
        long refreshTokenExpiryMs
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("storelense.jwt.secret must be at least 32 characters");
        }
    }
}
