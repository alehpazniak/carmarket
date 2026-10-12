package com.carmarket.chat.event;

import com.carmarket.chat.service.BlockedUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** auth-service → chat-service: blocked accounts can't connect or send (see StompPrincipalConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusEventConsumer {

    private final BlockedUserService blockedUsers;

    @KafkaListener(topics = "user.blocked", groupId = "chat-service")
    public void handleBlocked(UserStatusEvent event) {
        UUID userId = userId(event);
        if (userId != null) blockedUsers.block(userId, event.reason());
    }

    @KafkaListener(topics = "user.unblocked", groupId = "chat-service")
    public void handleUnblocked(UserStatusEvent event) {
        UUID userId = userId(event);
        if (userId != null) blockedUsers.unblock(userId);
    }

    private static UUID userId(UserStatusEvent event) {
        try {
            return UUID.fromString(event.userId());
        } catch (RuntimeException e) {
            log.warn("Ignoring user status event without a valid userId: {}", event);
            return null;
        }
    }
}
