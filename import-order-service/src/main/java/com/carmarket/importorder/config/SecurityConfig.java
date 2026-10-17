package com.carmarket.importorder.config;

import com.carmarket.importorder.security.GatewaySignatureFilter;
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
                // Client page: link + password → X-Order-Token, checked by ClientAccessService
                .requestMatchers(HttpMethod.POST, "/import-orders/public/*/access", "/import-orders/public/*/files").permitAll()
                .requestMatchers(HttpMethod.GET, "/import-orders/public/**").permitAll()
                // Must stay above the DEALER/ADMIN rule: the first match wins
                .requestMatchers("/import-orders/admin/**").hasRole("ADMIN")
                .requestMatchers("/import-orders", "/import-orders/**").hasAnyRole("DEALER", "ADMIN")
                .requestMatchers("/actuator/**").permitAll()
                // Error dispatches (e.g. a 400 for a bad parameter) must not turn into 403
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
