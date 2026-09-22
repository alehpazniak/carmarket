package com.carmarket.notification.service;

import com.carmarket.notification.entity.Notification;
import com.carmarket.notification.entity.NotificationStatus;
import com.carmarket.notification.entity.NotificationType;
import com.carmarket.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for every Kafka handler: queue an email for later ({@link #schedule}) or
 * drop it because the recipient already dealt with it ({@link #resolve}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final List<NotificationStatus> OPEN = List.of(NotificationStatus.PENDING, NotificationStatus.SENT);

    private final NotificationRepository repository;

    /**
     * Queues an email to go out after {@code delay}. Does nothing when this
     * (type, recipient, dedupKey) already has a pending or sent email that the
     * recipient hasn't acted on yet, so a burst of events yields one email.
     *
     * @return true if a new notification was queued
     */
    @Transactional
    public boolean schedule(NotificationType type, UUID recipientId, String dedupKey,
                            Duration delay, Map<String, String> payload) {
        if (repository.existsByTypeAndRecipientIdAndDedupKeyAndStatusIn(type, recipientId, dedupKey, OPEN)) {
            log.debug("Skipping {} for {}: already open for key {}", type, recipientId, dedupKey);
            return false;
        }
        repository.save(Notification.builder()
            .type(type)
            .recipientId(recipientId)
            .dedupKey(dedupKey)
            .sendAt(Instant.now().plus(delay))
            .payload(payload)
            .build());
        log.info("Queued {} for {} (key {}), due in {}", type, recipientId, dedupKey, delay);
        return true;
    }

    /**
     * The recipient acted on the thing we were going to tell them about: cancel a
     * pending email, and re-arm a key that was already emailed.
     */
    @Transactional
    public void resolve(NotificationType type, UUID recipientId, String dedupKey) {
        int cancelled = repository.transition(type, recipientId, dedupKey,
            NotificationStatus.PENDING, NotificationStatus.CANCELLED);
        int acknowledged = repository.transition(type, recipientId, dedupKey,
            NotificationStatus.SENT, NotificationStatus.ACKNOWLEDGED);
        if (cancelled + acknowledged > 0) {
            log.info("Resolved {} for {} (key {}): cancelled={}, acknowledged={}",
                type, recipientId, dedupKey, cancelled, acknowledged);
        }
    }
}
