package com.carmarket.notification.mail;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.entity.Recipient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailContentFactoryTest {

    @Test
    void dealerUpgradeEmailContainsConfirmLink() {
        Notification n = Notification.builder()
            .type(NotificationType.DEALER_UPGRADE_CONFIRMATION)
            .recipientId(UUID.randomUUID())
            .payload(Map.of("confirmUrl", "http://localhost:5173/konto-dealera/potwierdz?token=abc"))
            .build();

        EmailContent content = new EmailContentFactory().build(n,
            Recipient.builder().email("jan@example.com").displayName("Jan").build());

        assertThat(content.subject()).isEqualTo("Confirm switching to a dealer account");
        assertThat(content.body()).contains("Hello Jan", "http://localhost:5173/konto-dealera/potwierdz?token=abc");
    }
}
