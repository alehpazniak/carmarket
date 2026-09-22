package com.carmarket.notification.mail;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.Recipient;
import org.springframework.stereotype.Component;

/** Turns a queued notification into the subject and text of the email. One case per type. */
@Component
public class EmailContentFactory {

    public EmailContent build(Notification notification, Recipient recipient) {
        return switch (notification.getType()) {
            case NEW_CHAT_MESSAGE -> new EmailContent(
                "You got a new message",
                greeting(recipient) + "You got new message.\n\nOpen CarMarket to read and reply.\n");
        };
    }

    private String greeting(Recipient recipient) {
        String name = recipient.getDisplayName();
        return (name == null || name.isBlank()) ? "Hello,\n\n" : "Hello " + name + ",\n\n";
    }
}
