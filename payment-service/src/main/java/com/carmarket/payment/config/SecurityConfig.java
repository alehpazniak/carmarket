package com.carmarket.payment.config;

import com.carmarket.payment.security.GatewaySignatureFilter;
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
                // Przelewy24 webhook — verified by sign + transaction/verify, not by JWT
                .requestMatchers(HttpMethod.POST, "/payments/p24/notify").permitAll()
                .requestMatchers(HttpMethod.GET, "/payments/products").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/*/refund").hasRole("ADMIN")
                .requestMatchers("/payments/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
