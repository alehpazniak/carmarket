package com.carmarket.auth.service;

import com.carmarket.auth.entity.DealerUpgradeRequest;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.DealerUpgradeRequestRepository;
import com.carmarket.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Switches an account from USER to DEALER in two steps:
 * <ol>
 *   <li>{@link #request} stores a one-time token and publishes {@code user.dealer-upgrade.requested};
 *       notification-service emails the confirmation link built from it.</li>
 *   <li>{@link #confirm} redeems the token from that link, sets the role and publishes
 *       {@code user.role-changed} so user-service updates the profile.</li>
 * </ol>
 * The new role reaches the JWT on the next token refresh, since tokens are built from the DB role.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealerUpgradeService {

    static final String TOPIC_UPGRADE_REQUESTED = "user.dealer-upgrade.requested";
    static final String TOPIC_ROLE_CHANGED = "user.role-changed";
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DealerUpgradeRequestRepository requestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.dealer-upgrade.token-ttl:24h}")
    private Duration tokenTtl;

    @Transactional
    public LocalDateTime request(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole() != User.UserRole.USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already " + user.getRole());
        }
        requestRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
            .filter(r -> r.getConfirmedAt() == null && r.getCreatedAt() != null)
            .filter(r -> r.getCreatedAt().isAfter(LocalDateTime.now().minus(RESEND_COOLDOWN)))
            .ifPresent(r -> {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Confirmation email was just sent, try again in a minute");
            });

        requestRepository.deletePendingByUserId(userId);
        String token = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(tokenTtl);
        DealerUpgradeRequest saved = requestRepository.save(DealerUpgradeRequest.builder()
            .userId(userId)
            .tokenHash(hash(token))
            .expiresAt(expiresAt)
            .build());

        String confirmUrl = UriComponentsBuilder.fromUriString(frontendUrl)
            .path("/konto-dealera/potwierdz")
            .queryParam("token", token)
            .toUriString();
        kafkaTemplate.send(TOPIC_UPGRADE_REQUESTED, userId.toString(), Map.of(
            "requestId", saved.getId().toString(),
            "userId", userId.toString(),
            "email", user.getEmail(),
            "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
            "confirmUrl", confirmUrl,
            "expiresAt", expiresAt.toString()
        ));
        log.info("Dealer upgrade requested by user {}", userId);
        return expiresAt;
    }

    @Transactional
    public User confirm(String token) {
        DealerUpgradeRequest request = requestRepository.findByTokenHash(hash(token))
            .filter(r -> r.getConfirmedAt() == null)
            .filter(r -> r.getExpiresAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Confirmation link is invalid or has expired"));

        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Confirmation link is invalid or has expired"));
        request.setConfirmedAt(LocalDateTime.now());

        // Never downgrade an ADMIN who happened to have a pending link
        if (user.getRole() == User.UserRole.USER) {
            user.setRole(User.UserRole.DEALER);
            userRepository.save(user);
            kafkaTemplate.send(TOPIC_ROLE_CHANGED, user.getId().toString(), Map.of(
                "userId", user.getId().toString(),
                "role", user.getRole().name()
            ));
            log.info("User {} is now a DEALER", user.getId());
        }
        return user;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
