package com.carmarket.admin.audit;

import com.carmarket.admin.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditApiTest extends IntegrationTestBase {

    @Autowired
    private AdminAuditConsumer consumer;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE admin_audit_log");
    }

    private static AdminAuditEvent event(String action, String targetId, Instant at) {
        return new AdminAuditEvent(UUID.randomUUID(), at, "admin-1", "boss@example.com", "auth-service", action,
            "USER", targetId, "spam account", Map.of("from", "USER", "to", "DEALER"));
    }

    @Test
    void aRedeliveredEventIsStoredOnce() {
        AdminAuditEvent event = event("USER_BLOCKED", "u-1", Instant.now());

        consumer.onAudit(event);
        consumer.onAudit(event);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_log", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT details->>'to' FROM admin_audit_log", String.class)).isEqualTo("DEALER");
    }

    @Test
    void malformedEventsAreSkipped() {
        consumer.onAudit(new AdminAuditEvent(null, Instant.now(), "a", null, "s", "X", null, null, null, null));
        consumer.onAudit(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_log", Long.class)).isZero();
    }

    @Test
    void filtersAndOrdersNewestFirst() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        consumer.onAudit(event("USER_BLOCKED", "u-1", now.minus(2, ChronoUnit.DAYS)));
        consumer.onAudit(event("USER_UNBLOCKED", "u-1", now.minus(1, ChronoUnit.DAYS)));
        consumer.onAudit(event("USER_BLOCKED", "u-2", now));

        mvc.perform(get("/admin/audit").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)))
            .andExpect(jsonPath("$.content[0].targetId", is("u-2")))
            .andExpect(jsonPath("$.content[0].details.to", is("DEALER")));

        mvc.perform(get("/admin/audit").param("targetType", "USER").param("targetId", "u-1").with(admin()))
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.content[0].action", is("USER_UNBLOCKED")));

        mvc.perform(get("/admin/audit").param("action", "USER_BLOCKED")
                .param("from", now.minus(1, ChronoUnit.HOURS).toString()).with(admin()))
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].targetId", is("u-2")));
    }

    @Test
    void onlyAdminsMayReadTheLog() throws Exception {
        mvc.perform(get("/admin/audit").with(plainUser())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Roles", "ADMIN"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/system/health").with(plainUser())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").with(admin())).andExpect(status().isOk());
    }
}
