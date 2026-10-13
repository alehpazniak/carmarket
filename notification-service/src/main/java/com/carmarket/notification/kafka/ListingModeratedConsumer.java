package com.carmarket.notification.kafka;

import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.repository.RecipientRepository;
import com.carmarket.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * listing.moderated from car-service ({carId, sellerId, action BLOCKED|UNBLOCKED, reason, title,
 * occurredAt}): emails the seller right away. A seller without a Recipient row (registered before
 * notification-service existed) is skipped with a WARN — car-service has no email address to send.
 */
@Slf4j
@Component
public class ListingModeratedConsumer {

    private final NotificationService notificationService;
    private final RecipientRepository recipients;
    private final String frontendUrl;

    public ListingModeratedConsumer(NotificationService notificationService, RecipientRepository recipients,
                                    @Value("${notification.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.notificationService = notificationService;
        this.recipients = recipients;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Transactional
    @KafkaListener(topics = "listing.moderated", groupId = "notification-service")
    public void onListingModerated(Map<String, Object> event) {
        Object carId = event.get("carId");
        Object sellerId = event.get("sellerId");
        Object action = event.get("action");
        if (carId == null || sellerId == null || action == null) {
            log.warn("Ignoring malformed listing.moderated: {}", event.keySet());
            return;
        }
        NotificationType type = switch (action.toString()) {
            case "BLOCKED" -> NotificationType.LISTING_BLOCKED;
            case "UNBLOCKED" -> NotificationType.LISTING_RESTORED;
            default -> null;
        };
        if (type == null) {
            log.warn("Ignoring listing.moderated with unknown action {}", action);
            return;
        }
        UUID recipientId = UUID.fromString(sellerId.toString());
        if (!recipients.existsById(recipientId)) {
            log.warn("No recipient for seller {}: {} email for listing {} not sent", recipientId, type, carId);
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("link", frontendUrl + "/ogloszenia/" + carId);
        if (event.get("title") != null) payload.put("title", event.get("title").toString());
        if (event.get("reason") != null) payload.put("reason", event.get("reason").toString());
        // Each moderation decision is its own email (block, unblock, block again)
        String dedupKey = carId + ":" + action + ":" + event.getOrDefault("occurredAt", "");
        notificationService.schedule(type, recipientId, dedupKey.length() > 100 ? dedupKey.substring(0, 100) : dedupKey,
            Duration.ZERO, payload);
    }
}
