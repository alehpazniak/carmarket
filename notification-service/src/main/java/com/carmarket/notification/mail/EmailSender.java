package com.carmarket.notification.mail;

import com.carmarket.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    /** Throws a MailException if the SMTP server rejects or can't be reached. */
    public void send(String to, EmailContent content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(content.subject());
        message.setText(content.body());
        mailSender.send(message);
    }
}
