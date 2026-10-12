package com.carmarket.notification.kafka;

import com.carmarket.notification.config.NotificationProperties;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.repository.RecipientRepository;
import com.carmarket.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportOrderEventConsumerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final RecipientRepository recipients = mock(RecipientRepository.class);
    private ImportOrderEventConsumer consumer;

    private final String orderId = UUID.randomUUID().toString();
    private final UUID recipientId = UUID.nameUUIDFromBytes(("import-order-client:" + orderId).getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        consumer = new ImportOrderEventConsumer(notificationService, recipients,
            new NotificationProperties("no-reply@test", Duration.ofMinutes(2), 50, 3, Duration.ofMinutes(10)));
    }

    private Map<String, Object> event() {
        Map<String, Object> e = new HashMap<>();
        e.put("orderId", orderId);
        e.put("clientEmail", "klient@example.com");
        e.put("clientName", "Jan Kowalski");
        e.put("dealerName", "Auto Komis");
        e.put("carTitle", "Audi Q5 2019");
        e.put("link", "http://localhost:5173/zlecenie/abc");
        return e;
    }

    @Test
    void createsASyntheticRecipientAndBatchesForTenMinutes() {
        when(recipients.findById(recipientId)).thenReturn(Optional.empty());

        consumer.onClientUpdate(event());

        ArgumentCaptor<Recipient> saved = ArgumentCaptor.forClass(Recipient.class);
        verify(recipients).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(recipientId);
        assertThat(saved.getValue().getEmail()).isEqualTo("klient@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Jan Kowalski");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).scheduleBatched(eq(NotificationType.IMPORT_ORDER_UPDATE), eq(recipientId),
            eq(orderId), eq(Duration.ofMinutes(10)), payload.capture());
        assertThat(payload.getValue()).containsOnly(
            Map.entry("link", "http://localhost:5173/zlecenie/abc"),
            Map.entry("carTitle", "Audi Q5 2019"),
            Map.entry("dealerName", "Auto Komis"));
    }

    @Test
    void updatesTheEmailOfAnExistingRecipient() {
        Recipient existing = Recipient.builder().userId(recipientId).email("old@example.com").build();
        when(recipients.findById(recipientId)).thenReturn(Optional.of(existing));

        consumer.onClientUpdate(event());

        verify(recipients).save(existing);
        assertThat(existing.getEmail()).isEqualTo("klient@example.com");
    }

    @Test
    void ignoresEventsWithoutEmailOrLink() {
        Map<String, Object> noEmail = event();
        noEmail.remove("clientEmail");
        Map<String, Object> noLink = event();
        noLink.remove("link");

        consumer.onClientUpdate(noEmail);
        consumer.onClientUpdate(noLink);

        verifyNoInteractions(recipients);
        verify(notificationService, never()).scheduleBatched(any(), any(), any(), any(), anyMap());
    }
}
