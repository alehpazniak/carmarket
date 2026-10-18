package com.carmarket.auction.config;

import com.carmarket.auction.security.GatewaySignatureFilter;
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
 * Auction-import-service security.
 * JWT is validated by the gateway — this service trusts the X-User-Id / X-Gateway-Signature headers.
 * Mirrors car-service's SecurityConfig: GET on the synced lots is public (matches the gateway's
 * JwtAuthenticationFilter public-route list). The Apibara live lookups (quick check, vehicle preview,
 * shipping, max bid) are open to anonymous users too: they spend the account's monthly quota, so the
 * gateway gives anonymous callers a strict per-IP and global limiter (ApibaraRateLimiter). The account
 * usage, the image proxy and everything else require a valid gateway signature.
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
                // Must stay above every broader rule: the first match wins
                .requestMatchers("/api/auctions/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/auctions/lots", "/api/auctions/lots/**").permitAll()
                // Above the public Apibara rules: account usage and the image proxy stay for logged-in users
                .requestMatchers("/api/auctions/apibara/usage", "/api/auctions/apibara/image-proxy").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/auctions/apibara/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auctions/apibara/vehicles/*/max-bid").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
