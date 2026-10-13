package com.carmarket.auth.controller;

import com.carmarket.auth.dto.AuthResponse;
import com.carmarket.auth.dto.RefreshRequest;
import com.carmarket.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth REST endpoints exposed through the API Gateway at /api/auth/**
 * <p>
 * OAuth2 flow is handled by Spring Security (not these endpoints):
 * Initiate:  GET  /api/auth/oauth2/authorization/{provider}  → redirect to Google/Facebook
 * Callback:  GET  /api/auth/oauth2/callback/{provider}       → Spring Security handles it
 * <p>
 * These endpoints handle token management:
 * POST /api/auth/refresh                → exchange refresh token for new pair
 * POST /api/auth/logout                 → revoke refresh token
 * POST /api/auth/logout-all             → revoke all sessions (gateway-signed caller only)
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
    public ResponseEntity<Map<String, String>> logout(@RequestBody Map<String, String> body) {
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
     * Requires a gateway-signed request (SecurityConfig): the gateway only forwards it with a valid
     * access token, so nobody can log out someone else by calling port 8081 directly.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(@AuthenticationPrincipal String userId) {
        authService.logoutAllDevices(userId);
        return ResponseEntity.ok(Map.of("message", "All sessions terminated"));
    }

    /**
     * Health/debug — returns current user info from gateway headers.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal String userId,
                                                  @RequestHeader(value = "X-User-Email", required = false) String email,
                                                  @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ResponseEntity.ok(Map.of(
            "userId", userId != null ? userId : "",
            "email", email != null ? email : "",
            "roles", roles != null ? roles : ""
        ));
    }
}
