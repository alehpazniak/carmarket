package com.carmarket.notification.mail;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.Recipient;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Turns a queued notification into the subject and text of the email. One case per type. */
@Component
public class EmailContentFactory {

    public EmailContent build(Notification notification, Recipient recipient) {
        return switch (notification.getType()) {
            case NEW_CHAT_MESSAGE -> new EmailContent(
                "You got a new message",
                greeting(recipient) + "You got new message.\n\nOpen CarMarket to read and reply.\n");
            case DEALER_UPGRADE_CONFIRMATION -> new EmailContent(
                "Confirm switching to a dealer account",
                greeting(recipient)
                    + "We received a request to switch your CarMarket account to a dealer account.\n\n"
                    + "Confirm it by opening this link:\n"
                    + notification.getPayload().get("confirmUrl") + "\n\n"
                    + "The link is valid for 24 hours. If you didn't ask for this, ignore this email "
                    + "and your account stays unchanged.\n");
            case IMPORT_ORDER_UPDATE -> importOrderUpdate(notification);
        };
    }

    /** Polish: the client of a dealer, not a CarMarket user. Generic on purpose, never the password. */
    private EmailContent importOrderUpdate(Notification notification) {
        Map<String, String> p = notification.getPayload();
        String car = p.getOrDefault("carTitle", "Twój samochód");
        String dealer = p.get("dealerName");
        return new EmailContent(
            "Są nowe informacje o Twoim zleceniu — " + car,
            "Dzień dobry,\n\n"
                + "są nowe informacje o imporcie samochodu " + car
                + (dealer != null ? " od " + dealer : "") + ".\n\n"
                + "Szczegóły zobaczysz na stronie zlecenia:\n"
                + p.get("link") + "\n\n"
                + "Do wejścia potrzebne jest hasło, które otrzymałeś od dealera. "
                + "Ze względów bezpieczeństwa nie wysyłamy go e-mailem.\n");
    }

    private String greeting(Recipient recipient) {
        String name = recipient.getDisplayName();
        return (name == null || name.isBlank()) ? "Hello,\n\n" : "Hello " + name + ",\n\n";
    }
}
