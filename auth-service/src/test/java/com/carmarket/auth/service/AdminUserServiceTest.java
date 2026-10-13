package com.carmarket.auth.service;

import com.carmarket.auth.audit.AdminAuditPublisher;
import com.carmarket.auth.audit.AdminAuditPublisher.Actor;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.DealerUpgradeRequestRepository;
import com.carmarket.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AccessRevocationService revocation = mock(AccessRevocationService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
    private final AdminUserService service = new AdminUserService(users, mock(DealerUpgradeRequestRepository.class),
        refreshTokens, revocation, kafka, audit);

    private final User admin = user(User.UserRole.ADMIN);
    private final Actor actor = new Actor(admin.getId().toString(), "boss@example.com");

    private User user(User.UserRole role) {
        User u = User.builder().id(UUID.randomUUID()).email(UUID.randomUUID() + "@example.com").role(role).active(true).build();
        when(users.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void anAdminCantChangeTheirOwnRoleOrBlockThemselves() {
        assertConflict(() -> service.changeRole(admin.getId(), User.UserRole.USER, "stepping down", actor));
        assertConflict(() -> service.block(admin.getId(), "just testing", actor));
        assertThat(admin.getRole()).isEqualTo(User.UserRole.ADMIN);
        assertThat(admin.isActive()).isTrue();
        verifyNoInteractions(kafka, audit, revocation);
    }

    @Test
    void theLastActiveAdminCantBeDemotedOrBlocked() {
        User other = user(User.UserRole.ADMIN);
        when(users.lockActiveAdmins()).thenReturn(List.of(other));

        assertConflict(() -> service.changeRole(other.getId(), User.UserRole.USER, "no longer staff", actor));
        assertConflict(() -> service.block(other.getId(), "compromised", actor));
        assertThat(other.getRole()).isEqualTo(User.UserRole.ADMIN);
        assertThat(other.isActive()).isTrue();
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void demotingAnAdminWorksWhileAnotherRemains() {
        User other = user(User.UserRole.ADMIN);
        when(users.lockActiveAdmins()).thenReturn(List.of(other, admin));

        service.changeRole(other.getId(), User.UserRole.DEALER, "no longer staff", actor);

        assertThat(other.getRole()).isEqualTo(User.UserRole.DEALER);
        verify(kafka).send("user.role-changed", other.getId().toString(),
            Map.of("userId", other.getId().toString(), "role", "DEALER"));
        verify(revocation).invalidateIssuedTokens(other.getId().toString());
        verify(audit).publish(actor, "USER_ROLE_CHANGED", "USER", other.getId().toString(), "no longer staff",
            Map.of("from", "ADMIN", "to", "DEALER"));
    }

    @Test
    void blockingRevokesEverythingAndTellsTheOtherServices() {
        User target = user(User.UserRole.USER);

        service.block(target.getId(), "spam listings", actor);

        String id = target.getId().toString();
        assertThat(target.isActive()).isFalse();
        verify(revocation).markBlocked(id);
        verify(refreshTokens).revokeAll(id);
        verify(kafka).send(eq("user.blocked"), eq(id), argThat(e -> e instanceof Map<?, ?> m
            && id.equals(m.get("userId")) && "spam listings".equals(m.get("reason")) && m.get("occurredAt") != null));
        verify(audit).publish(eq(actor), eq("USER_BLOCKED"), eq("USER"), eq(id), eq("spam listings"), any());
    }

    @Test
    void unblockingClearsTheFlag() {
        User target = user(User.UserRole.USER);
        target.setActive(false);

        service.unblock(target.getId(), "appeal accepted", actor);

        assertThat(target.isActive()).isTrue();
        verify(revocation).clearBlocked(target.getId().toString());
        verify(kafka).send(eq("user.unblocked"), eq(target.getId().toString()), any());
        verify(audit).publish(eq(actor), eq("USER_UNBLOCKED"), eq("USER"), eq(target.getId().toString()), eq("appeal accepted"), any());
    }

    @Test
    void blockingAnAlreadyBlockedUserChangesNothing() {
        User target = user(User.UserRole.USER);
        target.setActive(false);

        service.block(target.getId(), "spam listings", actor);

        verifyNoInteractions(kafka, audit, revocation, refreshTokens);
    }

    @Test
    void aRedisFailureDoesNotUndoTheBlock() {
        User target = user(User.UserRole.USER);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down")).when(revocation).markBlocked(anyString());

        service.block(target.getId(), "spam listings", actor);

        assertThat(target.isActive()).isFalse();
        verify(kafka).send(eq("user.blocked"), anyString(), any());
    }

    @Test
    void adminLogoutRevokesRefreshTokensAndCurrentAccessTokens() {
        User target = user(User.UserRole.USER);

        service.logoutEverywhere(target.getId(), "lost phone", actor);

        verify(refreshTokens).revokeAll(target.getId().toString());
        verify(revocation).invalidateIssuedTokens(target.getId().toString());
        verify(audit).publish(actor, "USER_LOGGED_OUT", "USER", target.getId().toString(), "lost phone", Map.of());
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
