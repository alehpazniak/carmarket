package com.carmarket.auth.service;

import com.carmarket.auth.dto.RefreshRequest;
import com.carmarket.auth.entity.ProviderType;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.repository.UserRepository;
import com.carmarket.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A blocked account (users.is_active = false) can neither log in again nor refresh its session. */
class BlockedLoginTest {

    private static final String SECRET = java.util.Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(48));

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AdminBootstrapService bootstrap = mock(AdminBootstrapService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final JwtTokenProvider tokens = new JwtTokenProvider(SECRET);
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokens, "accessTokenExpiryMs", 900_000L);
        ReflectionTestUtils.setField(tokens, "refreshTokenExpiryMs", 604_800_000L);
        service = new AuthService(users, tokens, refreshTokens, kafka, bootstrap);
        user = User.builder().id(UUID.randomUUID()).email("jan@example.com").displayName("Jan")
            .role(User.UserRole.USER).active(false).build();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void oauthLoginOfABlockedAccountIsRefused() {
        OAuth2User oauth = mock(OAuth2User.class);
        when(oauth.getAttribute("sub")).thenReturn("google-1");
        when(oauth.getAttribute("email")).thenReturn("jan@example.com");
        when(users.findByProviderAndProviderId(ProviderType.GOOGLE, "google-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.handleOAuth2Login(oauth, ProviderType.GOOGLE))
            .isInstanceOf(AccountBlockedException.class);
        verify(refreshTokens, never()).store(anyString(), anyString(), anyLong());
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void loginSetsLastLoginAtForActiveAccounts() {
        user.setActive(true);
        OAuth2User oauth = mock(OAuth2User.class);
        when(oauth.getAttribute("sub")).thenReturn("google-1");
        when(oauth.getAttribute("email")).thenReturn("jan@example.com");
        when(users.findByProviderAndProviderId(ProviderType.GOOGLE, "google-1")).thenReturn(Optional.of(user));

        service.handleOAuth2Login(oauth, ProviderType.GOOGLE);

        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void refreshOfABlockedAccountIs403() {
        String refreshToken = tokens.generateRefreshToken(user);
        when(refreshTokens.isValid(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.refreshTokens(new RefreshRequest(refreshToken)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refreshTokens, never()).store(anyString(), anyString(), anyLong());
    }
}
