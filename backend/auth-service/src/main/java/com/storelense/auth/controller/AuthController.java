package com.storelense.auth.controller;

import com.storelense.auth.dto.LoginRequest;
import com.storelense.auth.dto.LoginResponse;
import com.storelense.auth.dto.RefreshTokenRequest;
import com.storelense.auth.service.AuthService;
import com.storelense.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, token refresh, logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive JWT tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {

        String ip = extractIp(http);
        LoginResponse resp = authService.login(request, ip);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and get new access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        LoginResponse resp = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke tokens and blacklist access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest http,
            @RequestBody(required = false) RefreshTokenRequest body) {

        String bearer = http.getHeader("Authorization");
        String accessToken = StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")
                ? bearer.substring(7) : null;
        String refreshToken = (body != null) ? body.refreshToken() : null;

        if (accessToken != null) {
            authService.logout(accessToken, refreshToken);
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info from token claims")
    public ResponseEntity<ApiResponse<Authentication>> me(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(auth));
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
