package com.carmarket.auth.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes admin.audit for every admin write; admin-service stores it in admin_audit_log.
 * Copied per service (no shared module, see CLAUDE.md) — keep the event shape in sync with
 * admin-service's AdminAuditEvent. Sent after commit, so a rolled-back action is never audited.
 * details is a small map of plain values: never secrets, tokens or whole documents.
 */
@Slf4j
@Component
public class AdminAuditPublisher {

    public static final String TOPIC = "admin.audit";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String service;

    public AdminAuditPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${spring.application.name}") String service) {
        this.kafkaTemplate = kafkaTemplate;
        this.service = service;
    }

    /** Who did it: the admin from the gateway headers, or {@link #SYSTEM} for automatic actions. */
    public record Actor(String id, String email) {
    }

    public static final Actor SYSTEM = new Actor("system", null);

    public record AdminAuditEvent(
        UUID eventId,
        Instant occurredAt,
        String actorId,
        String actorEmail,
        String service,
        String action,
        String targetType,
        String targetId,
        String reason,
        Map<String, Object> details
    ) {
    }

    public void publish(Actor actor, String action, String targetType, String targetId, String reason,
                        Map<String, ?> details) {
        Map<String, Object> cleanDetails = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((k, v) -> {
                if (v != null) cleanDetails.put(k, v);
            });
        }
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), Instant.now(), actor.id(), actor.email(),
            service, action, targetType, targetId, reason, Collections.unmodifiableMap(cleanDetails));
        Runnable send = () -> {
            kafkaTemplate.send(TOPIC, targetId != null ? targetId : event.eventId().toString(), event);
            log.info("Audit {} {} {}/{} by {}", service, action, targetType, targetId, actor.id());
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
