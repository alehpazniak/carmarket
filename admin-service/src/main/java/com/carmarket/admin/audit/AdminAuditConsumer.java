package com.carmarket.admin.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Stores admin.audit events from every service; malformed ones are logged and skipped. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditConsumer {

    private final AuditLogStore store;

    @KafkaListener(topics = "admin.audit", groupId = "admin-service")
    public void onAudit(AdminAuditEvent event) {
        if (event == null || event.eventId() == null || event.actorId() == null
            || event.service() == null || event.action() == null) {
            log.warn("Skipping malformed admin.audit event: {}", event);
            return;
        }
        if (store.save(event)) {
            log.info("Audit {} {} {}/{} by {}", event.service(), event.action(), event.targetType(),
                event.targetId(), event.actorId());
        } else {
            log.debug("Duplicate admin.audit event {} ignored", event.eventId());
        }
    }
}
