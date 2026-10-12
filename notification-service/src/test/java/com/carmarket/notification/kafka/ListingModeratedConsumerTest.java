package com.carmarket.notification.kafka;

import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.repository.RecipientRepository;
import com.carmarket.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingModeratedConsumerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final RecipientRepository recipients = mock(RecipientRepository.class);
    private final ListingModeratedConsumer consumer = new ListingModeratedConsumer(notifications, recipients, "http://localhost:5173/");
    private final UUID seller = UUID.randomUUID();
    private final String carId = UUID.randomUUID().toString();

    private Map<String, Object> event(String action) {
        return Map.of("carId", carId, "sellerId", seller.toString(), "action", action, "reason", "fake listing",
            "title", "Audi A4 (2019)", "occurredAt", "2026-10-12T10:00:00Z");
    }

    @Test
    void blockedListingEmailsTheSellerWithReasonAndLink() {
        when(recipients.existsById(seller)).thenReturn(true);

        consumer.onListingModerated(event("BLOCKED"));

        verify(notifications).schedule(eq(NotificationType.LISTING_BLOCKED), eq(seller),
            eq(carId + ":BLOCKED:2026-10-12T10:00:00Z"), eq(Duration.ZERO),
            eq(Map.of("link", "http://localhost:5173/ogloszenia/" + carId, "title", "Audi A4 (2019)", "reason", "fake listing")));
    }

    @Test
    void unblockSendsTheRestoredEmail() {
        when(recipients.existsById(seller)).thenReturn(true);
        consumer.onListingModerated(event("UNBLOCKED"));
        verify(notifications).schedule(eq(NotificationType.LISTING_RESTORED), eq(seller), anyString(), any(), any());
    }

    @Test
    void sellersWithoutARecipientRowAreSkipped() {
        when(recipients.existsById(seller)).thenReturn(false);
        consumer.onListingModerated(event("BLOCKED"));
        verify(notifications, never()).schedule(any(), any(), anyString(), any(), any());
    }
}
