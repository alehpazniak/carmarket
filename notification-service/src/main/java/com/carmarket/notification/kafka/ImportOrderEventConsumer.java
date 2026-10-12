package com.carmarket.notification.kafka;

import com.carmarket.notification.config.NotificationProperties;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.repository.RecipientRepository;
import com.carmarket.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * import-order.client-update: something new on an import order's client page. The client has no
 * account, so they get a synthetic recipient per order; updates within the configured window
 * (10 min by default) become one email. The event never carries the page password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportOrderEventConsumer {

    static final String TOPIC = "import-order.client-update";

    private final NotificationService notificationService;
    private final RecipientRepository recipients;
    private final NotificationProperties properties;

    /** The order's client as a recipient: one stable id per order, unrelated to any user id. */
    static UUID recipientId(String orderId) {
        return UUID.nameUUIDFromBytes(("import-order-client:" + orderId).getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void onClientUpdate(Map<String, Object> event) {
        Object orderId = event.get("orderId");
        Object email = event.get("clientEmail");
        Object link = event.get("link");
        if (orderId == null || email == null || email.toString().isBlank() || link == null) {
            log.warn("Ignoring malformed {}: {}", TOPIC, event.keySet());
            return;
        }
        UUID recipientId = recipientId(orderId.toString());

        // The dealer may have changed the client's email or name since the last update
        Recipient recipient = recipients.findById(recipientId)
            .orElseGet(() -> Recipient.builder().userId(recipientId).build());
        recipient.setEmail(email.toString());
        Object clientName = event.get("clientName");
        recipient.setDisplayName(clientName == null || clientName.toString().isBlank() ? null : clientName.toString());
        recipients.save(recipient);

        Map<String, String> payload = new HashMap<>();
        payload.put("link", link.toString());
        put(payload, "carTitle", event.get("carTitle"));
        put(payload, "dealerName", event.get("dealerName"));
        notificationService.scheduleBatched(NotificationType.IMPORT_ORDER_UPDATE, recipientId, orderId.toString(),
            properties.importOrderUpdateDelay(), payload);
    }

    private static void put(Map<String, String> payload, String key, Object value) {
        if (value != null && !value.toString().isBlank()) payload.put(key, value.toString());
    }
}
