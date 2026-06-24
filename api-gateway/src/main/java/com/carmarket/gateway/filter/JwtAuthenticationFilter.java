package com.carmarket.gateway.filter;

import com.carmarket.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Global JWT filter — runs on every request before routing.
 *
 * Flow:
 *  1. Check if route is public  → pass through
 *  2. Extract Bearer token from Authorization header
 *  3. Validate JWT signature + expiry (stateless, no auth-service call)
 *  4. Inject X-User-Id and X-User-Roles headers for downstream services
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private static final List<String> PUBLIC_ROUTES = List.of(
        "/api/auth/**",
        "/api/search/**",
        "/actuator/**",
        "/eureka/**"
    );
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        if (isPublicRoute(path, method)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            return onUnauthorized(exchange.getResponse(), "Missing Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.isValid(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return onUnauthorized(exchange.getResponse(), "Invalid or expired token");
        }

        Claims claims = jwtUtil.validateAndGetClaims(token);
        String userId = claims.getSubject();
        if (!isValidUuid(userId)) {
            log.warn("JWT subject is not a valid UUID for path {}: {}", path, userId);
            return onUnauthorized(exchange.getResponse(), "Invalid token subject");
        }
        String roles = String.join(",", jwtUtil.extractRoles(token));

        // Mutate request — add user context headers for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-User-Id", userId)
            .header("X-User-Roles", roles)
            .header("X-User-Email", claims.get("email", String.class))
            .build();

        log.debug("JWT validated for user: {} path: {}", userId, path);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicRoute(String path, String method) {
        if ("GET".equals(method) &&
            (pathMatcher.match("/api/cars", path) || pathMatcher.match("/api/cars/**", path))) {
            return true;
        }
        return PUBLIC_ROUTES.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> onUnauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        var body = response.bufferFactory()
            .wrap(("{\"error\":\"" + message + "\"}").getBytes());
        return response.writeWith(Mono.just(body));
    }

    private boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        // Run before routing filters
        return -100;
    }
}
