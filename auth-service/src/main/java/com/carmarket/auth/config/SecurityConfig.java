package com.carmarket.auth.config;

import com.carmarket.auth.security.OAuth2FailureHandler;
import com.carmarket.auth.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auth service security configuration.
 *
 * The auth-service itself is the OAuth2 client — it handles callbacks from
 * Google/Facebook and issues JWTs. No JWT filter here (that's in the gateway).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String AUTHORIZATION_BASE_URI = "/auth/oauth2/authorization";

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrations) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // All auth endpoints are public — gateway handles auth for other services
                .requestMatchers("/auth/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint -> endpoint
                    .baseUri(AUTHORIZATION_BASE_URI)
                    .authorizationRequestResolver(accountChooserResolver(clientRegistrations)))
                .redirectionEndpoint(endpoint ->
                    endpoint.baseUri("/auth/oauth2/callback/*"))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            );

        return http.build();
    }

    /**
     * Adds prompt=select_account so Google always shows its account chooser. Without it,
     * a user already signed in to Google is logged straight back in after logging out and
     * can't switch to a different account.
     */
    private OAuth2AuthorizationRequestResolver accountChooserResolver(ClientRegistrationRepository clientRegistrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
            new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, AUTHORIZATION_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(request ->
            request.additionalParameters(params -> params.put("prompt", "select_account")));
        return resolver;
    }
}
