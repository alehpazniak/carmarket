package com.carmarket.notification.kafka;

import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Keeps a local copy of who to email, from the user.registered events auth-service publishes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final RecipientRepository recipients;

    @KafkaListener(topics = "user.registered", groupId = "notification-service")
    public void onUserRegistered(Map<String, Object> event) {
        Object userId = event.get("userId");
        Object email = event.get("email");
        if (userId == null || email == null) {
            log.warn("Ignoring user.registered without userId/email: {}", event.keySet());
            return;
        }
        Object displayName = event.get("displayName");
        recipients.save(Recipient.builder()
            .userId(UUID.fromString(userId.toString()))
            .email(email.toString())
            .displayName(displayName == null ? null : displayName.toString())
            .build());
    }
}
