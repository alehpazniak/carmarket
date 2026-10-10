package com.carmarket.notification.kafka;

import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.repository.RecipientRepository;
import com.carmarket.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * user.dealer-upgrade.requested: auth-service wants the user to confirm switching to a dealer account,
 * so email them the confirmation link right away. One email per request (deduplicated by requestId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealerUpgradeEventConsumer {

    private final NotificationService notificationService;
    private final RecipientRepository recipients;

    @Transactional
    @KafkaListener(topics = "user.dealer-upgrade.requested", groupId = "notification-service")
    public void onUpgradeRequested(Map<String, Object> event) {
        Object userId = event.get("userId");
        Object requestId = event.get("requestId");
        Object email = event.get("email");
        Object confirmUrl = event.get("confirmUrl");
        if (userId == null || requestId == null || email == null || confirmUrl == null) {
            log.warn("Ignoring malformed user.dealer-upgrade.requested: {}", event.keySet());
            return;
        }
        UUID recipientId = UUID.fromString(userId.toString());

        // Users who registered before notification-service existed have no recipient row yet
        Recipient recipient = recipients.findById(recipientId)
            .orElseGet(() -> Recipient.builder().userId(recipientId).build());
        recipient.setEmail(email.toString());
        Object displayName = event.get("displayName");
        if (displayName != null && !displayName.toString().isBlank()) {
            recipient.setDisplayName(displayName.toString());
        }
        recipients.save(recipient);

        notificationService.schedule(NotificationType.DEALER_UPGRADE_CONFIRMATION, recipientId,
            requestId.toString(), Duration.ZERO, Map.of("confirmUrl", confirmUrl.toString()));
    }
}
