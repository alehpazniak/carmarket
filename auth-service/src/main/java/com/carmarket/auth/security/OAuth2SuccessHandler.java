package com.carmarket.auth.security;

import com.carmarket.auth.dto.AuthResponse;
import com.carmarket.auth.entity.ProviderType;
import com.carmarket.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * Called by Spring Security after a successful OAuth2 callback.
 *
 * Two modes:
 *  1. If frontend sends a redirect_uri param → redirect with tokens in query params
 *  2. Otherwise → return JSON response (REST flow for SPAs/mobile apps)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/auth/callback}")
    private String defaultRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "google" or "facebook"

        ProviderType provider = ProviderType.valueOf(registrationId.toUpperCase());
        AuthResponse authResponse = authService.handleOAuth2Login(oAuth2User, provider);

        String redirectUri = getRedirectUri(request);

        if (isAllowedRedirectUri(redirectUri)) {
            // Redirect mode (web app with frontend)
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("access_token", authResponse.getAccessToken())
                .queryParam("refresh_token", authResponse.getRefreshToken())
                .queryParam("user_id", authResponse.getUserId())
                .build().toUriString();

            log.debug("Redirecting OAuth2 success to: {}", redirectUri);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            // JSON mode (REST clients / mobile)
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), authResponse);
        }
    }

    private String getRedirectUri(HttpServletRequest request) {
        String param = request.getParameter("redirect_uri");
        return (param != null && !param.isBlank()) ? param : defaultRedirectUri;
    }

    private boolean isAllowedRedirectUri(String uri) {
        // Basic validation — in prod, maintain an allowlist
        try {
            URI parsed = URI.create(uri);
            String host = parsed.getHost();
            return host != null && !host.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
