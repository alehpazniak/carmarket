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
    void listingEmailsArePolishWithReasonAndLink() {
        Recipient seller = Recipient.builder().email("jan@example.com").displayName("Jan").build();
        Map<String, String> payload = Map.of("title", "Audi A4 (2019)", "reason", "Zdjęcia nie przedstawiają auta",
            "link", "http://localhost:5173/ogloszenia/abc");

        EmailContent blocked = new EmailContentFactory().build(Notification.builder()
            .type(NotificationType.LISTING_BLOCKED).recipientId(UUID.randomUUID()).payload(payload).build(), seller);
        EmailContent restored = new EmailContentFactory().build(Notification.builder()
            .type(NotificationType.LISTING_RESTORED).recipientId(UUID.randomUUID()).payload(payload).build(), seller);

        assertThat(blocked.subject()).isEqualTo("Twoje ogłoszenie zostało zablokowane — Audi A4 (2019)");
        assertThat(blocked.body()).contains("Dzień dobry Jan", "Powód: Zdjęcia nie przedstawiają auta",
            "http://localhost:5173/ogloszenia/abc");
        assertThat(restored.subject()).startsWith("Twoje ogłoszenie jest znowu widoczne");
        assertThat(restored.body()).contains("http://localhost:5173/ogloszenia/abc");
    }

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

    @Test
    void importOrderUpdateEmailIsPolishWithLinkAndNoPassword() {
        Notification n = Notification.builder()
            .type(NotificationType.IMPORT_ORDER_UPDATE)
            .recipientId(UUID.randomUUID())
            .payload(Map.of("link", "http://localhost:5173/zlecenie/abcDEF123", "carTitle", "Audi Q5 2019",
                "dealerName", "Auto Komis"))
            .build();

        EmailContent content = new EmailContentFactory().build(n,
            Recipient.builder().email("klient@example.com").displayName("Jan").build());

        assertThat(content.subject()).isEqualTo("Są nowe informacje o Twoim zleceniu — Audi Q5 2019");
        assertThat(content.body())
            .contains("Dzień dobry", "Audi Q5 2019", "od Auto Komis", "http://localhost:5173/zlecenie/abcDEF123")
            .contains("nie wysyłamy go e-mailem")
            .doesNotContainIgnoringCase("hasło:");
    }

    @Test
    void importOrderUpdateEmailWorksWithoutOptionalFields() {
        Notification n = Notification.builder()
            .type(NotificationType.IMPORT_ORDER_UPDATE)
            .recipientId(UUID.randomUUID())
            .payload(Map.of("link", "http://localhost:5173/zlecenie/x"))
            .build();

        EmailContent content = new EmailContentFactory().build(n, Recipient.builder().email("k@example.com").build());

        assertThat(content.subject()).endsWith("Twój samochód");
        assertThat(content.body()).doesNotContain("null").contains("http://localhost:5173/zlecenie/x");
    }
}
