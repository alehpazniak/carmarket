package com.carmarket.auth.controller;

import com.carmarket.auth.entity.User;
import com.carmarket.auth.security.JwtTokenProvider;
import com.carmarket.auth.service.DealerUpgradeService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * USER → DEALER account switch, confirmed by email.
 * <p>
 * POST /auth/dealer-upgrade          → sends the confirmation email (Bearer access token required)
 * POST /auth/dealer-upgrade/confirm  → redeems the emailed token, no login needed
 * <p>
 * /api/auth/** is public at the gateway, which strips X-User-Id there, so the request endpoint
 * validates the access token itself instead of trusting identity headers.
 */
@RestController
@RequestMapping("/auth/dealer-upgrade")
@RequiredArgsConstructor
public class DealerUpgradeController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DealerUpgradeService dealerUpgradeService;
    private final JwtTokenProvider jwtTokenProvider;

    public record ConfirmRequest(@NotBlank @Size(max = 100) String token) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> request(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        LocalDateTime expiresAt = dealerUpgradeService.request(authenticatedUserId(authorization));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "message", "Confirmation email sent",
            "expiresAt", expiresAt.toString()
        ));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@Valid @RequestBody ConfirmRequest request) {
        User user = dealerUpgradeService.confirm(request.token());
        return ResponseEntity.ok(Map.of(
            "userId", user.getId().toString(),
            "role", user.getRole().name()
        ));
    }

    private UUID authenticatedUserId(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        try {
            Claims claims = jwtTokenProvider.validateAndGetClaims(authorization.substring(BEARER_PREFIX.length()));
            if ("refresh".equals(claims.get("type"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token required");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
