package com.storelense.auth.service;

import com.storelense.auth.domain.entity.RefreshToken;
import com.storelense.auth.domain.entity.User;
import com.storelense.auth.domain.repository.RefreshTokenRepository;
import com.storelense.auth.domain.repository.UserRepository;
import com.storelense.auth.dto.LoginRequest;
import com.storelense.auth.dto.LoginResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.security.JwtProperties;
import com.storelense.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository          userRepository;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final JwtTokenProvider        tokenProvider;
    private final JwtProperties           jwtProperties;
    private final PasswordEncoder         passwordEncoder;
    private final StringRedisTemplate     redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS",
                        "Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!user.isActive()) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        if (user.isLocked()) {
            throw new BusinessException("ACCOUNT_LOCKED",
                    "Account locked until " + user.getLockedUntil(), HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            userRepository.incrementFailedAttempts(user.getId(),
                    OffsetDateTime.now().plus(LOCK_DURATION));
            throw new BusinessException("INVALID_CREDENTIALS",
                    "Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        userRepository.recordSuccessfulLogin(user.getId(), OffsetDateTime.now());

        String accessToken  = tokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getPrimaryRole(), user.getStoreId());
        String rawRefresh   = UUID.randomUUID().toString();
        String refreshHash  = sha256(rawRefresh);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(refreshHash)
                .expiresAt(OffsetDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshTokenExpiryMs())))
                .ipAddress(ipAddress)
                .build());

        return LoginResponse.of(
                accessToken, rawRefresh,
                jwtProperties.accessTokenExpiryMs() / 1000,
                user.getId(), user.getUsername(), user.getPrimaryRole(), user.getStoreId());
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN",
                        "Refresh token not found", HttpStatus.UNAUTHORIZED));

        if (!stored.isActive()) {
            throw new BusinessException("TOKEN_EXPIRED", "Refresh token expired or revoked", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.UNAUTHORIZED));

        if (!user.isActive()) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: revoke old, issue new
        stored.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(stored);

        String newAccessToken = tokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getPrimaryRole(), user.getStoreId());
        String newRawRefresh  = UUID.randomUUID().toString();
        String newHash        = sha256(newRawRefresh);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(newHash)
                .expiresAt(OffsetDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshTokenExpiryMs())))
                .build());

        return LoginResponse.of(
                newAccessToken, newRawRefresh,
                jwtProperties.accessTokenExpiryMs() / 1000,
                user.getId(), user.getUsername(), user.getPrimaryRole(), user.getStoreId());
    }

    @Transactional
    public void logout(String accessToken, String rawRefreshToken) {
        // Blacklist the access token in Redis until it expires
        if (tokenProvider.isValid(accessToken)) {
            String jti = tokenProvider.getJti(accessToken);
            long ttlMs = tokenProvider.getExpiry(accessToken).getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                redisTemplate.opsForValue().set(
                        "jwt:blacklist:" + jti, "1", Duration.ofMillis(ttlMs));
            }
        }

        if (rawRefreshToken != null) {
            String hash = sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
                t.setRevokedAt(OffsetDateTime.now());
                refreshTokenRepository.save(t);
            });
        }
    }

    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("jwt:blacklist:" + jti));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
