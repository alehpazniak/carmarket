package com.carmarket.notification.admin;

import com.carmarket.notification.audit.AdminAuditPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminNotificationService.class)
@Testcontainers
class AdminNotificationServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private AdminAuditPublisher audit;
    @Autowired
    private AdminNotificationService service;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID recipient = UUID.randomUUID();

    private UUID insert(String status, int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO notifications (id, type, recipient_id, dedup_key, status, send_at, attempts, last_error, payload)
            VALUES (?, 'LISTING_BLOCKED', ?, ?, ?, now(), ?, 'smtp down', '{"link":"x"}'::jsonb)""",
            id, recipient, id.toString(), status, attempts);
        return id;
    }

    @BeforeEach
    void recipient() {
        jdbc.update("INSERT INTO recipients (user_id, email) VALUES (?, 'jan@example.com')", recipient);
    }

    @Test
    void retryRequeuesAFailedEmailAndResetsAttempts() {
        UUID failed = insert("FAILED", 3);

        service.retry(failed, new AdminAuditPublisher.Actor("admin-1", "boss@example.com"));

        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempts, last_error FROM notifications WHERE id = ?", failed);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 0).containsEntry("last_error", null);
        verify(audit).publish(any(), eq("NOTIFICATION_RETRIED"), eq("NOTIFICATION"), eq(failed.toString()), isNull(),
            eq(Map.of("type", "LISTING_BLOCKED")));
    }

    @Test
    void onlyFailedEmailsCanBeRetried() {
        UUID sent = insert("SENT", 1);
        assertThatThrownBy(() -> service.retry(sent, new AdminAuditPublisher.Actor("a", null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> service.retry(UUID.randomUUID(), new AdminAuditPublisher.Actor("a", null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listJoinsTheRecipientEmailAndStatsCount() {
        insert("FAILED", 3);
        insert("SENT", 1);

        var failed = service.list(com.carmarket.notification.entity.NotificationStatus.FAILED, null, 0, 20);
        assertThat(failed.getTotalElements()).isEqualTo(1);
        assertThat(failed.getContent().get(0).recipientEmail()).isEqualTo("jan@example.com");
        assertThat(failed.getContent().get(0).lastError()).isEqualTo("smtp down");

        var stats = service.stats();
        assertThat(stats.byStatus()).containsEntry("FAILED", 1L).containsEntry("SENT", 1L);
        assertThat(stats.failedByType()).containsEntry("LISTING_BLOCKED", 1L);
    }
}
