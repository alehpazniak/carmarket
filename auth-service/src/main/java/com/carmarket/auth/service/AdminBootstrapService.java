package com.carmarket.auth.service;

import com.carmarket.auth.audit.AdminAuditPublisher;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.support.AfterCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * First admins: app.admin.bootstrap-emails (env ADMIN_EMAILS, comma-separated) are promoted to ADMIN
 * when they log in. Only ever promotes: removing an address from the list never demotes anyone,
 * that is done in the admin panel.
 */
@Slf4j
@Service
public class AdminBootstrapService {

    static final String TOPIC_ROLE_CHANGED = "user.role-changed";

    private final Set<String> emails;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdminAuditPublisher auditPublisher;

    public AdminBootstrapService(@Value("${app.admin.bootstrap-emails:}") String emails,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 AdminAuditPublisher auditPublisher) {
        this.emails = Arrays.stream(emails == null ? new String[0] : emails.split(","))
            .map(e -> e.trim().toLowerCase(Locale.ROOT))
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.kafkaTemplate = kafkaTemplate;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Sets role ADMIN on a listed user who isn't one yet (the caller saves the entity) and audits it.
     * With {@code publishRoleChanged}, user-service is told after commit; a brand-new user doesn't
     * need it, since its user.registered event already carries the new role.
     *
     * @return whether the role changed
     */
    public boolean promoteIfListed(User user, boolean publishRoleChanged) {
        if (user.getEmail() == null || user.getRole() == User.UserRole.ADMIN
            || !emails.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
            return false;
        }
        User.UserRole previous = user.getRole();
        user.setRole(User.UserRole.ADMIN);
        log.warn("User {} promoted to ADMIN by ADMIN_EMAILS", user.getId());

        String userId = user.getId() != null ? user.getId().toString() : null;
        if (publishRoleChanged) {
            AfterCommit.run(() -> kafkaTemplate.send(TOPIC_ROLE_CHANGED, userId,
                Map.of("userId", userId, "role", User.UserRole.ADMIN.name())));
        }
        auditPublisher.publish(AdminAuditPublisher.SYSTEM, "USER_ROLE_CHANGED", "USER", userId,
            "ADMIN_EMAILS bootstrap", Map.of("from", previous.name(), "to", User.UserRole.ADMIN.name()));
        return true;
    }
}
