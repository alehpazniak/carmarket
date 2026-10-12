package com.carmarket.auth.config;

import com.carmarket.auth.security.GatewaySignatureFilter;
import com.carmarket.auth.security.OAuth2FailureHandler;
import com.carmarket.auth.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auth service security configuration.
 *
 * The auth-service itself is the OAuth2 client — it handles callbacks from
 * Google/Facebook and issues JWTs. No JWT filter here (that's in the gateway).
 * <p>
 * OAuth2, refresh, logout and dealer-upgrade stay public (dealer-upgrade checks the bearer token
 * itself). Calls that act on the caller's identity need a gateway-signed X-User-Id
 * ({@link GatewaySignatureFilter}), since port 8081 is published.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String AUTHORIZATION_BASE_URI = "/auth/oauth2/authorization";

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final GatewaySignatureFilter gatewaySignatureFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrations) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter.class)
            // 401 instead of oauth2Login's redirect to the provider's login page
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Must stay above the /auth/** permitAll rule: the first match wins
                .requestMatchers("/auth/admin/**").hasRole("ADMIN")
                .requestMatchers("/auth/logout-all", "/auth/me").authenticated()
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
