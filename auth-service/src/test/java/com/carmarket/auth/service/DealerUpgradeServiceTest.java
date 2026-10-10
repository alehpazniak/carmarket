package com.carmarket.auth.service;

import com.carmarket.auth.entity.DealerUpgradeRequest;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.DealerUpgradeRequestRepository;
import com.carmarket.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DealerUpgradeService Test")
@ExtendWith(MockitoExtension.class)
class DealerUpgradeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DealerUpgradeRequestRepository requestRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DealerUpgradeService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "tokenTtl", Duration.ofHours(24));
        user = User.builder().id(UUID.randomUUID()).email("jan@example.com").displayName("Jan")
            .role(User.UserRole.USER).build();
    }

    @Test
    @DisplayName("request stores only the token hash and emails a link carrying the raw token")
    @SuppressWarnings("unchecked")
    void requestPublishesConfirmLink() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(requestRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(inv -> {
            DealerUpgradeRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        service.request(user.getId());

        ArgumentCaptor<DealerUpgradeRequest> saved = ArgumentCaptor.forClass(DealerUpgradeRequest.class);
        verify(requestRepository).deletePendingByUserId(user.getId());
        verify(requestRepository).save(saved.capture());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(DealerUpgradeService.TOPIC_UPGRADE_REQUESTED), eq(user.getId().toString()), event.capture());

        Map<String, String> payload = (Map<String, String>) event.getValue();
        assertThat(payload.get("email")).isEqualTo("jan@example.com");
        assertThat(payload.get("confirmUrl")).startsWith("http://localhost:5173/konto-dealera/potwierdz?token=");
        String token = payload.get("confirmUrl").substring(payload.get("confirmUrl").indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(DealerUpgradeService.hash(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
    }

    @Test
    @DisplayName("request is rejected for an account that is not USER")
    void requestRejectsDealer() {
        user.setRole(User.UserRole.DEALER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.request(user.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("request is throttled right after a previous email")
    void requestIsThrottled() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(requestRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.of(
            DealerUpgradeRequest.builder().createdAt(LocalDateTime.now().minusSeconds(10)).build()));

        assertThatThrownBy(() -> service.request(user.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(requestRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("confirm switches USER to DEALER, marks the link used and publishes user.role-changed")
    void confirmUpgrades() {
        DealerUpgradeRequest request = DealerUpgradeRequest.builder().userId(user.getId())
            .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(requestRepository.findByTokenHash(DealerUpgradeService.hash("tok"))).thenReturn(Optional.of(request));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = service.confirm("tok");

        assertThat(result.getRole()).isEqualTo(User.UserRole.DEALER);
        assertThat(request.getConfirmedAt()).isNotNull();
        verify(kafkaTemplate).send(DealerUpgradeService.TOPIC_ROLE_CHANGED, user.getId().toString(),
            Map.of("userId", user.getId().toString(), "role", "DEALER"));
    }

    @Test
    @DisplayName("confirm rejects an expired link")
    void confirmRejectsExpired() {
        when(requestRepository.findByTokenHash(DealerUpgradeService.hash("tok"))).thenReturn(Optional.of(
            DealerUpgradeRequest.builder().userId(user.getId()).expiresAt(LocalDateTime.now().minusMinutes(1)).build()));

        assertThatThrownBy(() -> service.confirm("tok"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("confirm rejects a link that was already used")
    void confirmRejectsUsed() {
        when(requestRepository.findByTokenHash(DealerUpgradeService.hash("tok"))).thenReturn(Optional.of(
            DealerUpgradeRequest.builder().userId(user.getId()).expiresAt(LocalDateTime.now().plusHours(1))
                .confirmedAt(LocalDateTime.now().minusMinutes(5)).build()));

        assertThatThrownBy(() -> service.confirm("tok")).isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm never downgrades an ADMIN")
    void confirmKeepsAdmin() {
        user.setRole(User.UserRole.ADMIN);
        when(requestRepository.findByTokenHash(DealerUpgradeService.hash("tok"))).thenReturn(Optional.of(
            DealerUpgradeRequest.builder().userId(user.getId()).expiresAt(LocalDateTime.now().plusHours(1)).build()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.confirm("tok").getRole()).isEqualTo(User.UserRole.ADMIN);
        verifyNoInteractions(kafkaTemplate);
    }
}
