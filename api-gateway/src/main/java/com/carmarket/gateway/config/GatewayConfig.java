package com.carmarket.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
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
     * Key resolver: use X-User-Id if present (authenticated), else the client IP (see {@link ClientIpResolver}).
     */
    @Bean
    @Primary
    public KeyResolver keyResolver(ClientIpResolver clientIp) {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) return Mono.just(userId);
            return Mono.just(clientIp.resolve(exchange));
        };
    }

    /**
     * Strict limiter for auth endpoints — anti-bruteforce.
     * replenishRate=5/s, burst=10, 1 token per request.
     */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }

    /**
     * Standard limiter for general API traffic.
     * replenishRate=20/s, burst=40.
     */
    @Bean
    @Primary
    public RedisRateLimiter standardRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }

    /**
     * Phone-number lookups (GET /api/users/{id}/phone) — anti-harvesting, per user.
     * <p>
     * RedisRateLimiter is a token bucket refilled at replenishRate tokens per second up to
     * burstCapacity, and each request costs requestedTokens. With (1, 300, 30):
     * burst = 300 / 30 = 10 requests, then one request per 30 tokens / 1 token/s = 30 s,
     * i.e. 2 per minute; an idle user is back to a full burst after 300 s.
     */
    @Bean
    public RedisRateLimiter phoneRateLimiter() {
        return new RedisRateLimiter(1, 300, 30);
    }

    /**
     * RedisRateLimiter keys its Redis bucket by the resolved key only, not by route, so the phone
     * route needs its own key; with the plain user id it would share the standard limiter's bucket.
     * The route requires a JWT, so X-User-Id is always set by JwtAuthenticationFilter.
     */
    static final KeyResolver PHONE_KEY_RESOLVER = exchange ->
        Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-User-Id")).map(userId -> "phone:" + userId);

    /**
     * Client page password check (POST /api/import-orders/public/{code}/access) — anti-bruteforce per IP,
     * on top of the per-order lockout in import-order-service: burst 10, then 1 per second.
     */
    @Bean
    public RedisRateLimiter importOrderAccessRateLimiter() {
        return new RedisRateLimiter(1, 10, 1);
    }

    /**
     * The access route is public (no X-User-Id), so it is keyed by IP — but under its own prefix: with the
     * plain IP it would share the Redis bucket of every other anonymous request from that address, and
     * the standard limiter's refill would undo the strict one.
     */
    static KeyResolver importAccessKeyResolver(ClientIpResolver clientIp) {
        return exchange -> Mono.just("import-access:" + clientIp.resolve(exchange));
    }

    /** The client page's uploads: same limiter as the password check, but its own bucket. */
    static KeyResolver importUploadKeyResolver(ClientIpResolver clientIp) {
        return exchange -> Mono.just("import-upload:" + clientIp.resolve(exchange));
    }

    /**
     * "Wyświetl numer" on a public dealer page: anonymous, so keyed by IP under its own prefix
     * (see importAccessKeyResolver). Same rate as the seller phone: burst 10, then 2 per minute.
     */
    @Bean
    public RedisRateLimiter dealerPhoneRateLimiter() {
        return new RedisRateLimiter(1, 300, 30);
    }

    static KeyResolver dealerPhoneKeyResolver(ClientIpResolver clientIp) {
        return exchange -> Mono.just("dealer-phone:" + clientIp.resolve(exchange));
    }

    /**
     * The public dealer map (GET /api/users/dealers/nearby): anonymous, keyed by IP under its own prefix
     * (see importAccessKeyResolver). With (1, 60, 2): burst 30, then one request per 2 s = 30 per minute,
     * enough for panning and changing the radius with the frontend's 300 ms debounce.
     */
    @Bean
    public RedisRateLimiter dealerMapRateLimiter() {
        return new RedisRateLimiter(1, 60, 2);
    }

    static KeyResolver dealerMapKeyResolver(ClientIpResolver clientIp) {
        return exchange -> Mono.just("dealer-map:" + clientIp.resolve(exchange));
    }

    /**
     * Anonymous Apibara live lookups, per IP (see {@link ApibaraRateLimiter}): {@code per-ip-per-hour}
     * requests an hour, all usable at once.
     */
    @Bean
    public RedisRateLimiter apibaraAnonymousIpRateLimiter(
            @Value("${gateway.apibara-anonymous.per-ip-per-hour:5}") int perHour) {
        return ApibaraRateLimiter.perPeriod(perHour, 3600);
    }

    /** All anonymous Apibara live lookups together: {@code global-per-day} requests a day. */
    @Bean
    public RedisRateLimiter apibaraAnonymousGlobalRateLimiter(
            @Value("${gateway.apibara-anonymous.global-per-day:30}") int perDay) {
        return ApibaraRateLimiter.perPeriod(perDay, 86400);
    }

    /** Logged-in users by user id (the standard bucket); anonymous callers under their own prefix and IP. */
    static KeyResolver apibaraKeyResolver(ClientIpResolver clientIp) {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) return Mono.just(userId);
            return Mono.just(ApibaraRateLimiter.ANONYMOUS_PREFIX + clientIp.resolve(exchange));
        };
    }

    /*
     * Limiters are picked by @Qualifier: @Primary wins over a matching parameter name, so without it
     * every RedisRateLimiter parameter would get standardRateLimiter.
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               KeyResolver keyResolver,
                               ClientIpResolver clientIp,
                               @Qualifier("authRateLimiter") RedisRateLimiter authRateLimiter,
                               @Qualifier("standardRateLimiter") RedisRateLimiter standardRateLimiter,
                               @Qualifier("phoneRateLimiter") RedisRateLimiter phoneRateLimiter,
                               @Qualifier("importOrderAccessRateLimiter") RedisRateLimiter importOrderAccessRateLimiter,
                               @Qualifier("dealerPhoneRateLimiter") RedisRateLimiter dealerPhoneRateLimiter,
                               @Qualifier("dealerMapRateLimiter") RedisRateLimiter dealerMapRateLimiter,
                               @Qualifier("apibaraAnonymousIpRateLimiter") RedisRateLimiter apibaraAnonymousIpRateLimiter,
                               @Qualifier("apibaraAnonymousGlobalRateLimiter") RedisRateLimiter apibaraAnonymousGlobalRateLimiter) {
        ApibaraRateLimiter apibaraRateLimiter = new ApibaraRateLimiter(
            standardRateLimiter, apibaraAnonymousIpRateLimiter, apibaraAnonymousGlobalRateLimiter);
        return builder.routes()

            // ─── AUTH SERVICE ────────────────────────────────────────────
            .route("auth-service", r -> r
                .path("/api/auth/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("auth-cb").setFallbackUri("forward:/fallback/auth"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(authRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/auth/(?<segment>.*)", "/auth/${segment}"))
                .uri("lb://auth-service"))

            // ─── USER SERVICE — dealer map, public (must stay above the generic user routes) ───
            .route("user-service-dealer-map", r -> r
                .path("/api/users/dealers/nearby")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(dealerMapRateLimiter)
                        .setKeyResolver(dealerMapKeyResolver(clientIp)))
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}"))
                .uri("lb://user-service"))

            // ─── USER SERVICE — dealer page phone, public (must stay above the generic user routes) ───
            .route("user-service-dealer-phone", r -> r
                .path("/api/users/dealers/*/phone")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(dealerPhoneRateLimiter)
                        .setKeyResolver(dealerPhoneKeyResolver(clientIp)))
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}"))
                .uri("lb://user-service"))

            // ─── USER SERVICE — seller phone (must stay above the generic user routes) ───
            .route("user-service-phone", r -> r
                .path("/api/users/*/phone")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(phoneRateLimiter)
                        .setKeyResolver(PHONE_KEY_RESOLVER))
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}"))
                .uri("lb://user-service"))

            // ─── USER SERVICE — root ──────────────────────────────────────
            .route("user-service-root", r -> r
                .path("/api/users")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/users", "/users"))
                .uri("lb://user-service"))

            // ─── USER SERVICE — with path ─────────────────────────────────
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("user-cb").setFallbackUri("forward:/fallback/user"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/users/(?<segment>.*)", "/users/${segment}"))
                .uri("lb://user-service"))
// ─── CAR SERVICE — root ───────────────────────────────────────
            .route("car-service-root", r -> r
                .path("/api/cars")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("car-cb").setFallbackUri("forward:/fallback/car"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/cars", "/cars"))
                .uri("lb://car-service"))

            // ─── CAR SERVICE — with path ──────────────────────────────────
            .route("car-service", r -> r
                .path("/api/cars/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("car-cb").setFallbackUri("forward:/fallback/car"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/cars/(?<segment>.*)", "/cars/${segment}"))
                .uri("lb://car-service"))

            // ─── SEARCH SERVICE — root ────────────────────────────────────
            .route("search-service-root", r -> r
                .path("/api/search")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("search-cb").setFallbackUri("forward:/fallback/search"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/search", "/search"))
                .uri("lb://search-service"))

            // ─── SEARCH SERVICE — with path ───────────────────────────────
            .route("search-service", r -> r
                .path("/api/search/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("search-cb").setFallbackUri("forward:/fallback/search"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/search/(?<segment>.*)", "/search/${segment}"))
                .uri("lb://search-service"))

            // ─── CHAT SERVICE — with path ───────────────────────────────
            .route("chat-service", r -> r
                .path("/api/chat/**")
                .filters(f -> f
                    .rewritePath("/api/chat/(?<segment>.*)", "/chat/${segment}"))
                .uri("lb://chat-service"))

            // ─── AUCTION IMPORT SERVICE — Apibara live lookups, also anonymous (must stay above the generic route) ───
            .route("auction-import-service-apibara", r -> r
                .path("/api/auctions/apibara/**")
                .filters(f -> f
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(apibaraRateLimiter)
                        .setKeyResolver(apibaraKeyResolver(clientIp))))
                .uri("lb://auction-import-service"))

            // ─── AUCTION IMPORT SERVICE — with path ───────────────────────────────
            .route("auction-import-service", r -> r
                .path("/api/auctions/**")
                .filters(f -> f
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver)))
                .uri("lb://auction-import-service"))

            // ─── PAYMENT SERVICE (Przelewy24) ─────────────────────────────
            .route("payment-service", r -> r
                .path("/api/payments/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("payment-cb").setFallbackUri("forward:/fallback/payment"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/payments/(?<segment>.*)", "/payments/${segment}"))
                .uri("lb://payment-service"))

            .route("payment-service-root", r -> r
                .path("/api/payments")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("payment-cb").setFallbackUri("forward:/fallback/payment"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/payments", "/payments"))
                .uri("lb://payment-service"))

            // ─── IMPORT ORDER SERVICE — client password check (must stay above the other import-order routes) ───
            .route("import-order-service-access", r -> r
                .path("/api/import-orders/public/*/access").and().method(HttpMethod.POST)
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("import-order-cb").setFallbackUri("forward:/fallback/import-order"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(importOrderAccessRateLimiter)
                        .setKeyResolver(importAccessKeyResolver(clientIp)))
                    .rewritePath("/api/import-orders/(?<segment>.*)", "/import-orders/${segment}"))
                .uri("lb://import-order-service"))

            // ─── IMPORT ORDER SERVICE — the client's uploads (e.g. payment confirmation): public, so keyed by IP
            //     under its own prefix, and no circuit breaker (its time limit would cut off large uploads) ───
            .route("import-order-service-client-files", r -> r
                .path("/api/import-orders/public/*/files").and().method(HttpMethod.POST)
                .filters(f -> f
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(importOrderAccessRateLimiter)
                        .setKeyResolver(importUploadKeyResolver(clientIp)))
                    .rewritePath("/api/import-orders/(?<segment>.*)", "/import-orders/${segment}"))
                .uri("lb://import-order-service"))

            // ─── IMPORT ORDER SERVICE — uploads: no circuit breaker, its time limit would cut off large uploads ───
            .route("import-order-service-files", r -> r
                .path("/api/import-orders/*/files").and().method(HttpMethod.POST)
                .filters(f -> f
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/import-orders/(?<segment>.*)", "/import-orders/${segment}"))
                .uri("lb://import-order-service"))

            // Root before /** — "/**" also matches the bare path, and then the rewrite below would not apply
            .route("import-order-service-root", r -> r
                .path("/api/import-orders")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("import-order-cb").setFallbackUri("forward:/fallback/import-order"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/import-orders", "/import-orders"))
                .uri("lb://import-order-service"))

            .route("import-order-service", r -> r
                .path("/api/import-orders/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("import-order-cb").setFallbackUri("forward:/fallback/import-order"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/import-orders/(?<segment>.*)", "/import-orders/${segment}"))
                .uri("lb://import-order-service"))

            // ─── ADMIN SERVICE (audit log, system health) — never public, see JwtAuthenticationFilter ───
            .route("admin-service", r -> r
                .path("/api/admin/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("admin-cb").setFallbackUri("forward:/fallback/admin"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/admin/(?<segment>.*)", "/admin/${segment}"))
                .uri("lb://admin-service"))

            // ─── NOTIFICATION SERVICE — admin panel only; the service has no other HTTP API ───
            .route("notification-service-admin", r -> r
                .path("/api/notifications/admin/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("notification-cb").setFallbackUri("forward:/fallback/notification"))
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver))
                    .rewritePath("/api/notifications/admin/(?<segment>.*)", "/notifications/admin/${segment}"))
                .uri("lb://notification-service"))

            .route("auction-analytics", r -> r
                .path("/api/analytics/**")
                .filters(f -> f
                    .requestRateLimiter(rl -> rl
                        .setRateLimiter(standardRateLimiter)
                        .setKeyResolver(keyResolver)))
                .uri("lb://auction-import-service"))

            .build();
    }

}
