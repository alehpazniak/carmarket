package com.carmarket.auth.controller;

import com.carmarket.auth.dto.AuthResponse;
import com.carmarket.auth.dto.RefreshRequest;
import com.carmarket.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth REST endpoints exposed through the API Gateway at /api/auth/**
 *
 * OAuth2 flow is handled by Spring Security (not these endpoints):
 *   Initiate:  GET  /api/auth/oauth2/authorization/{provider}  → redirect to Google/Facebook
 *   Callback:  GET  /api/auth/oauth2/callback/{provider}       → Spring Security handles it
 *
 * These endpoints handle token management:
 *   POST /api/auth/refresh                → exchange refresh token for new pair
 *   POST /api/auth/logout                 → revoke refresh token
 *   POST /api/auth/logout-all             → revoke all sessions (requires X-User-Id header)
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Token refresh endpoint.
     * Client sends expired access token + valid refresh token → gets new pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refreshTokens(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout — revokes the provided refresh token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
        @RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "refresh_token is required"));
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Logout all devices — revokes all refresh tokens for a user.
     * Requires a valid access token (X-User-Id injected by gateway).
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(
        @RequestHeader("X-User-Id") String userId) {
        authService.logoutAllDevices(userId);
        return ResponseEntity.ok(Map.of("message", "All sessions terminated"));
    }

    /**
     * Health/debug — returns current user info from gateway headers.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestHeader(value = "X-User-Email", required = false) String email,
        @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ResponseEntity.ok(Map.of(
            "userId", userId != null ? userId : "",
            "email", email != null ? email : "",
            "roles", roles != null ? roles : ""
        ));
    }
}
