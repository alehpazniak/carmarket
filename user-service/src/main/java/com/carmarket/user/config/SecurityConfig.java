package com.carmarket.user.config;

import com.carmarket.user.security.GatewaySignatureFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewaySignatureFilter gatewaySignatureFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Error dispatches (e.g. 400 for a bad query param) carry no identity; without this
                // they'd be turned into 403 by anyRequest().authenticated()
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // Admin panel: full profiles (PII), ADMIN only; must stay above every broader rule
                .requestMatchers("/users/admin/**").hasRole("ADMIN")
                // The gateway routes all of /api/users/**, so these are reachable by any logged-in user
                .requestMatchers(HttpMethod.POST, "/users/*/increment-listings", "/users/*/decrement-listings").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/users/me").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/users/me/contact").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/users/me").authenticated()
                // Public dealer pages and their "Wyświetl numer"; above the seller phone rule, which
                // would otherwise match /users/dealers/phone
                .requestMatchers(HttpMethod.GET, "/users/dealers/*", "/users/dealers/*/phone", "/users/dealers/by-user/*").permitAll()
                // Must stay above GET /users/{id}: the phone is only for logged-in users
                .requestMatchers(HttpMethod.GET, "/users/*/phone").authenticated()
                .requestMatchers(HttpMethod.GET, "/users/{id}").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated() // ← was permitAll(); now requires valid gateway signature
            );
        return http.build();
    }
}
