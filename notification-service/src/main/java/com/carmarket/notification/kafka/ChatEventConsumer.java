package com.carmarket.notification.kafka;

import com.carmarket.notification.config.NotificationProperties;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * chat.message.sent: one participant wrote to the other, so email the recipient unless they read it in time.
 * chat.conversation.read: cancels the pending email (or re-arms it if it was already sent).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventConsumer {

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    @KafkaListener(topics = "chat.message.sent", groupId = "notification-service")
    public void onMessageSent(Map<String, Object> event) {
        UUID recipientId = id(event, "recipientId");
        UUID conversationId = id(event, "conversationId");
        if (recipientId == null || conversationId == null) {
            log.warn("Ignoring malformed chat.message.sent: {}", event.keySet());
            return;
        }
        notificationService.schedule(NotificationType.NEW_CHAT_MESSAGE, recipientId,
            conversationId.toString(), properties.chatMessageDelay(),
            Map.of("conversationId", conversationId.toString(),
                "carId", String.valueOf(event.get("carId"))));
    }

    @KafkaListener(topics = "chat.conversation.read", groupId = "notification-service")
    public void onConversationRead(Map<String, Object> event) {
        UUID readerId = id(event, "readerId");
        UUID conversationId = id(event, "conversationId");
        if (readerId == null || conversationId == null) {
            log.warn("Ignoring malformed chat.conversation.read: {}", event.keySet());
            return;
        }
        notificationService.resolve(NotificationType.NEW_CHAT_MESSAGE, readerId, conversationId.toString());
    }

    private UUID id(Map<String, Object> event, String field) {
        Object v = event.get(field);
        return v == null ? null : UUID.fromString(v.toString());
    }
}
