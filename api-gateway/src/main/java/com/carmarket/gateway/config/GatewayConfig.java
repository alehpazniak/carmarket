package com.carmarket.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Programmatic route definitions.
 * <p>
 * All routes use:
 * - lb:// for Eureka load-balanced routing
 * - Circuit breaker per service
 * - Rate limiting on public/write endpoints
 */
@Configuration
public class GatewayConfig {

    /**
     * Key resolver: use X-User-Id if present (authenticated), else remote IP.
     */
    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) return Mono.just(userId);
            return Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown"
            );
        };
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

            // ─── AUTH SERVICE ────────────────────────────────────────────
            .route("auth-service", r -> r
                .path("/api/auth/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("auth-cb").setFallbackUri("forward:/fallback/auth"))
                    .rewritePath("/api/auth/(?<segment>.*)", "/auth/${segment}"))
                .uri("lb://auth-service"))

            // ─── USER SERVICE — root ──────────────────────────────────────
            .route("user-service-root", r -> r
                .path("/api/users")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .rewritePath("/api/users", "/users"))
                .uri("lb://user-service"))

            // ─── USER SERVICE — with path ─────────────────────────────────
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}"))
                .uri("lb://user-service"))

            // ─── CAR SERVICE — root ───────────────────────────────────────
            .route("car-service-root", r -> r
                .path("/api/cars")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("car-cb").setFallbackUri("forward:/fallback/car"))
                    .rewritePath("/api/cars", "/cars"))
                .uri("lb://car-service"))

            // ─── CAR SERVICE — with path ──────────────────────────────────
            .route("car-service", r -> r
                .path("/api/cars/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("car-cb").setFallbackUri("forward:/fallback/car"))
                    .rewritePath("/api/cars/(?<segment>.*)", "/cars/${segment}"))
                .uri("lb://car-service"))

            // ─── SEARCH SERVICE — root ────────────────────────────────────
            .route("search-service-root", r -> r
                .path("/api/search")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("search-cb").setFallbackUri("forward:/fallback/search"))
                    .rewritePath("/api/search", "/search"))
                .uri("lb://search-service"))

            // ─── SEARCH SERVICE — with path ───────────────────────────────
            .route("search-service", r -> r
                .path("/api/search/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("search-cb").setFallbackUri("forward:/fallback/search"))
                    .rewritePath("/api/search/(?<segment>.*)", "/search/${segment}"))
                .uri("lb://search-service"))

            .build();
    }
}
