package com.storelense.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long   expiresIn,
        UUID   userId,
        String username,
        String role,
        UUID   storeId
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn,
                                    UUID userId, String username, String role, UUID storeId) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, userId, username, role, storeId);
    }
}
