package com.carmarket.car.config;

import com.carmarket.car.security.GatewaySignatureFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Car-service security.
 * JWT is validated by the gateway — this service trusts the X-User-Id header.
 * No JWT filter here; just allow/deny based on route rules.
 * All write operations are "authenticated" only at the gateway level.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewaySignatureFilter gatewaySignatureFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewaySignatureFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/cars/admin/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/cars/favorites").authenticated()
                .requestMatchers(HttpMethod.GET, "/cars/*/favorite").authenticated()
                .requestMatchers(HttpMethod.GET, "/cars", "/cars/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated() // ← was permitAll(); now requires valid gateway signature
            );
        return http.build();
    }
}
