package com.carmarket.user.kafka;

import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes user.registered events from auth-service.
 * Creates the user profile in user-service DB automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserProfileRepository userProfileRepository;

    @KafkaListener(topics = "user.registered", groupId = "user-service")
    public void handleUserRegistered(Map<String, String> event) {
        String userId = event.get("userId");
        log.info("Received user.registered event for userId: {}", userId);

        UUID id = UUID.fromString(userId);

        if (userProfileRepository.existsById(id)) {
            log.warn("UserProfile already exists for userId: {}", userId);
            return;
        }

        UserProfile profile = UserProfile.builder()
            .id(id)
            .email(event.get("email"))
            .displayName(event.get("displayName"))
            .avatarUrl(event.get("avatarUrl"))
            .role(UserProfile.UserRole.valueOf(event.get("role")))
            .active(true)
            .build();

        userProfileRepository.save(profile);
        log.info("UserProfile created for: {}", event.get("email"));
    }

    /** user.blocked: an admin blocked the account in auth-service; the public profile disappears (404). */
    @KafkaListener(topics = "user.blocked", groupId = "user-service")
    public void handleBlocked(Map<String, String> event) {
        setActive(event, false);
    }

    /** user.unblocked: the account is usable again. */
    @KafkaListener(topics = "user.unblocked", groupId = "user-service")
    public void handleUnblocked(Map<String, String> event) {
        setActive(event, true);
    }

    private void setActive(Map<String, String> event, boolean active) {
        String userId = event.get("userId");
        if (userId == null) {
            log.warn("Ignoring user status event without userId: {}", event.keySet());
            return;
        }
        userProfileRepository.findById(UUID.fromString(userId)).ifPresentOrElse(profile -> {
            profile.setActive(active);
            userProfileRepository.save(profile);
            log.info("UserProfile {} is now {}", userId, active ? "active" : "blocked");
        }, () -> log.warn("No UserProfile for status change of userId: {}", userId));
    }

    /** user.role-changed: auth-service changed the account role, e.g. USER → DEALER after email confirmation. */
    @KafkaListener(topics = "user.role-changed", groupId = "user-service")
    public void handleRoleChanged(Map<String, String> event) {
        String userId = event.get("userId");
        String role = event.get("role");
        if (userId == null || role == null) {
            log.warn("Ignoring malformed user.role-changed: {}", event.keySet());
            return;
        }
        userProfileRepository.findById(UUID.fromString(userId)).ifPresentOrElse(profile -> {
            profile.setRole(UserProfile.UserRole.valueOf(role));
            userProfileRepository.save(profile);
            log.info("UserProfile {} role changed to {}", userId, role);
        }, () -> log.warn("No UserProfile for role change of userId: {}", userId));
    }
}
