package com.carmarket.auth.service;

import com.carmarket.auth.dto.AuthResponse;
import com.carmarket.auth.dto.RefreshRequest;
import com.carmarket.auth.entity.OAuthProvider;
import com.carmarket.auth.entity.OAuthProvider.ProviderType;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.UserRepository;
import com.carmarket.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core authentication logic.
 *
 * Handles:
 *  - OAuth2 login success (Google / Facebook) → find-or-create user → issue tokens
 *  - Token refresh
 *  - Logout (revoke refresh token)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_USER_REGISTERED = "user.registered";

    // ─── OAuth2 SUCCESS ────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse handleOAuth2Login(OAuth2User oAuth2User, ProviderType provider) {
        String providerUserId = extractProviderId(oAuth2User, provider);
        String email = oAuth2User.getAttribute("email");
        String name = extractName(oAuth2User, provider);
        String avatar = extractAvatar(oAuth2User, provider);

        // 1. Find by provider identity
        Optional<User> existingByProvider = userRepository
            .findByProviderAndProviderId(provider, providerUserId);

        User user;
        boolean isNewUser = false;

        if (existingByProvider.isPresent()) {
            // Existing user via this provider — just update token
            user = existingByProvider.get();
            updateProviderToken(user, provider, oAuth2User);
        } else {
            // 2. Maybe user exists with same email (different provider) → link
            Optional<User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent()) {
                user = existingByEmail.get();
                linkProvider(user, provider, providerUserId, oAuth2User);
                log.info("Linked {} to existing account for email: {}", provider, email);
            } else {
                // 3. Brand new user — create and publish event
                user = createUser(email, name, avatar, provider, providerUserId, oAuth2User);
                isNewUser = true;
                log.info("New user registered via {}: {}", provider, email);
            }
        }

        if (isNewUser) {
            publishUserRegisteredEvent(user);
        }

        return issueTokens(user);
    }

    // ─── TOKEN REFRESH ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse refreshTokens(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        Claims claims = jwtTokenProvider.validateAndGetClaims(refreshToken);

        if (!"refresh".equals(claims.get("type"))) {
            throw new IllegalArgumentException("Not a refresh token");
        }

        String userId = claims.getSubject();
        String jti = claims.getId();

        if (!refreshTokenService.isValid(userId, jti)) {
            // Token was revoked (logout) or already rotated
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        // Rotate: revoke old token, issue new pair
        refreshTokenService.revoke(userId, jti);

        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return issueTokens(user);
    }

    // ─── LOGOUT ────────────────────────────────────────────────────────────────

    public void logout(String refreshToken) {
        if (jwtTokenProvider.isValid(refreshToken)) {
            String userId = jwtTokenProvider.extractUserId(refreshToken);
            String jti = jwtTokenProvider.extractJti(refreshToken);
            refreshTokenService.revoke(userId, jti);
            log.info("User {} logged out, refresh token revoked", userId);
        }
    }

    public void logoutAllDevices(String userId) {
        refreshTokenService.revokeAll(userId);
        log.info("All sessions revoked for user: {}", userId);
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Store refresh token in Redis
        String jti = jwtTokenProvider.extractJti(refreshToken);
        refreshTokenService.store(
            user.getId().toString(),
            jti,
            jwtTokenProvider.getRefreshTokenExpiryMs()
        );

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(900)          // 15 min in seconds
            .userId(user.getId().toString())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .role(user.getRole().name())
            .build();
    }

    private User createUser(String email, String name, String avatar,
                            ProviderType provider, String providerUserId,
                            OAuth2User oAuth2User) {
        OAuthProvider oauthProvider = OAuthProvider.builder()
            .provider(provider)
            .providerUserId(providerUserId)
            .accessToken(oAuth2User.getAttribute("access_token"))
            .linkedAt(LocalDateTime.now())
            .build();

        User user = User.builder()
            .email(email)
            .displayName(name)
            .avatarUrl(avatar)
            .role(User.UserRole.USER)
            .emailVerified(true) // OAuth2 emails are pre-verified
            .active(true)
            .build();

        oauthProvider.setUser(user);
        user.getProviders().add(oauthProvider);

        return userRepository.save(user);
    }

    private void linkProvider(User user, ProviderType provider,
                              String providerUserId, OAuth2User oAuth2User) {
        OAuthProvider oauthProvider = OAuthProvider.builder()
            .user(user)
            .provider(provider)
            .providerUserId(providerUserId)
            .accessToken(oAuth2User.getAttribute("access_token"))
            .linkedAt(LocalDateTime.now())
            .build();
        user.getProviders().add(oauthProvider);
        userRepository.save(user);
    }

    private void updateProviderToken(User user, ProviderType provider, OAuth2User oAuth2User) {
        user.getProviders().stream()
            .filter(p -> p.getProvider() == provider)
            .findFirst()
            .ifPresent(p -> p.setAccessToken(oAuth2User.getAttribute("access_token")));
        userRepository.save(user);
    }

    private String extractProviderId(OAuth2User oAuth2User, ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> oAuth2User.getAttribute("sub");
            case FACEBOOK -> oAuth2User.getAttribute("id");
        };
    }

    private String extractName(OAuth2User oAuth2User, ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> oAuth2User.getAttribute("name");
            case FACEBOOK -> oAuth2User.getAttribute("name");
        };
    }

    private String extractAvatar(OAuth2User oAuth2User, ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> oAuth2User.getAttribute("picture");
            case FACEBOOK -> {
                Map<String, Object> picture = oAuth2User.getAttribute("picture");
                if (picture != null) {
                    Map<String, Object> data = (Map<String, Object>) picture.get("data");
                    yield data != null ? (String) data.get("url") : null;
                }
                yield null;
            }
        };
    }

    private void publishUserRegisteredEvent(User user) {
        var event = Map.of(
            "userId", user.getId().toString(),
            "email", user.getEmail(),
            "displayName", user.getDisplayName(),
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "role", user.getRole().name(),
            "createdAt", user.getCreatedAt().toString()
        );
        kafkaTemplate.send(TOPIC_USER_REGISTERED, user.getId().toString(), event);
        log.info("Published user.registered event for: {}", user.getEmail());
    }
}
