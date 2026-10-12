package com.carmarket.auth.service;

import com.carmarket.auth.audit.AdminAuditPublisher;
import com.carmarket.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminBootstrapServiceTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final AdminAuditPublisher audit = mock(AdminAuditPublisher.class);

    private AdminBootstrapService service(String emails) {
        return new AdminBootstrapService(emails, kafka, audit);
    }

    private static User user(String email, User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email(email).role(role).build();
    }

    @Test
    void promotesAListedUserCaseInsensitivelyAndAuditsAsSystem() {
        User user = user("Boss@Example.com", User.UserRole.DEALER);

        boolean promoted = service(" other@example.com , boss@example.com ").promoteIfListed(user, true);

        assertThat(promoted).isTrue();
        assertThat(user.getRole()).isEqualTo(User.UserRole.ADMIN);
        verify(kafka).send(AdminBootstrapService.TOPIC_ROLE_CHANGED, user.getId().toString(),
            Map.of("userId", user.getId().toString(), "role", "ADMIN"));
        verify(audit).publish(eq(AdminAuditPublisher.SYSTEM), eq("USER_ROLE_CHANGED"), eq("USER"),
            eq(user.getId().toString()), anyString(), eq(Map.of("from", "DEALER", "to", "ADMIN")));
    }

    @Test
    void newUsersAreNotSentRoleChangedBecauseUserRegisteredCarriesTheRole() {
        User user = user("boss@example.com", User.UserRole.USER);

        assertThat(service("boss@example.com").promoteIfListed(user, false)).isTrue();

        verify(kafka, never()).send(anyString(), anyString(), any());
        verify(audit).publish(any(), eq("USER_ROLE_CHANGED"), any(), any(), any(), any());
    }

    @Test
    void leavesUnlistedUsersAndExistingAdminsAlone() {
        User plain = user("someone@example.com", User.UserRole.USER);
        User admin = user("boss@example.com", User.UserRole.ADMIN);

        assertThat(service("boss@example.com").promoteIfListed(plain, true)).isFalse();
        assertThat(service("boss@example.com").promoteIfListed(admin, true)).isFalse();
        assertThat(service("").promoteIfListed(user("boss@example.com", User.UserRole.USER), true)).isFalse();

        assertThat(plain.getRole()).isEqualTo(User.UserRole.USER);
        verifyNoInteractions(kafka, audit);
    }
}
