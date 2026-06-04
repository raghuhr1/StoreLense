package com.storelense.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, String username, String role, UUID storeId) {
        var now = new Date();
        var expiry = new Date(now.getTime() + jwtProperties.accessTokenExpiryMs());

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .claim("username", username)
                .claim("role", role);

        if (storeId != null) {
            builder.claim("storeId", storeId.toString());
        }

        return builder.signWith(signingKey()).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public UUID getStoreId(String token) {
        String storeId = parseToken(token).get("storeId", String.class);
        return storeId != null ? UUID.fromString(storeId) : null;
    }

    public Date getExpiry(String token) {
        return parseToken(token).getExpiration();
    }
}
