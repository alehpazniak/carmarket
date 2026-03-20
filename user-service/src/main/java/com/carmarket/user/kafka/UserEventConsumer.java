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
}
