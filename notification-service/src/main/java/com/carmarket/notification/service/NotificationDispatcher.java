package com.carmarket.notification.service;

import com.carmarket.notification.config.NotificationProperties;
import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.Recipient;
import com.carmarket.notification.mail.EmailContentFactory;
import com.carmarket.notification.mail.EmailSender;
import com.carmarket.notification.repository.NotificationRepository;
import com.carmarket.notification.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Polls the queue and sends every email that has come due. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final NotificationRepository notifications;
    private final RecipientRepository recipients;
    private final EmailContentFactory contentFactory;
    private final EmailSender emailSender;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${notification.dispatch-interval-ms:15000}")
    @Transactional
    public void dispatchDue() {
        List<Notification> due = notifications.lockDue(Instant.now(), properties.batchSize());
        for (Notification n : due) {
            dispatch(n);
        }
    }

    private void dispatch(Notification n) {
        Optional<Recipient> recipient = recipients.findById(n.getRecipientId());
        if (recipient.isEmpty()) {
            fail(n, "No email known for recipient " + n.getRecipientId());
            return;
        }
        try {
            emailSender.send(recipient.get().getEmail(), contentFactory.build(n, recipient.get()));
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setLastError(null);
            log.info("Sent {} email to user {}", n.getType(), n.getRecipientId());
        } catch (Exception e) {
            n.setAttempts(n.getAttempts() + 1);
            n.setLastError(truncate(e.getMessage()));
            if (n.getAttempts() >= properties.maxAttempts()) {
                log.error("Giving up on {} for user {} after {} attempts", n.getType(), n.getRecipientId(), n.getAttempts(), e);
                n.setStatus(NotificationStatus.FAILED);
            } else {
                log.warn("Sending {} to user {} failed (attempt {}): {}", n.getType(), n.getRecipientId(), n.getAttempts(), e.getMessage());
                n.setSendAt(Instant.now().plus(RETRY_BACKOFF.multipliedBy(n.getAttempts())));
            }
        }
    }

    private void fail(Notification n, String reason) {
        log.warn("{}", reason);
        n.setStatus(NotificationStatus.FAILED);
        n.setLastError(truncate(reason));
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
