package com.carmarket.car.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Car-service security.
 * JWT is validated by the gateway — this service trusts the X-User-Id header.
 * No JWT filter here; just allow/deny based on route rules.
 * All write operations are "authenticated" only at the gateway level.
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
                .requestMatchers(HttpMethod.GET, "/cars", "/cars/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll() // gateway enforces auth; downstream trusts headers
            );
        return http.build();
    }
}
