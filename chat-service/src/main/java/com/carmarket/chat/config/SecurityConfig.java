package com.carmarket.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * REST endpoints: auth is enforced at the gateway; /ws handshake auth is done
 * by JwtHandshakeInterceptor. So HTTP security here is permissive but stateless.
 *
 * NOTE: unlike car/user-service, this service does NOT yet verify the gateway
 * signature on REST calls. If you want the same hardening, port GatewaySignatureFilter
 * here too. For the WebSocket path, the JWT handshake interceptor already secures it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**").permitAll()          // handshake secured by interceptor
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll()                        // REST secured at gateway
            );
        return http.build();
    }
}
