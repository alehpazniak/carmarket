package com.carmarket.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Rate limiter of the Apibara live lookups (/api/auctions/apibara/**), which spend the account's monthly
 * quota and are open to anonymous users.
 * <p>
 * Logged-in users (key = their user id) use the standard limiter, i.e. the same bucket as before. Anonymous
 * calls (key = {@link #ANONYMOUS_PREFIX} + IP, see {@link GatewayConfig#apibaraKeyResolver}) must pass both
 * a per-IP bucket and one global bucket shared by every anonymous caller: per IP alone can't protect a quota
 * that is shared by everyone, a few addresses would use it up.
 */
public class ApibaraRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    static final String ANONYMOUS_PREFIX = "apibara-anon:";
    static final String GLOBAL_ANONYMOUS_KEY = "apibara-anon-global";

    private final RateLimiter<RedisRateLimiter.Config> authenticated;
    private final RateLimiter<RedisRateLimiter.Config> anonymousPerIp;
    private final RateLimiter<RedisRateLimiter.Config> anonymousGlobal;

    public ApibaraRateLimiter(RateLimiter<RedisRateLimiter.Config> authenticated,
                              RateLimiter<RedisRateLimiter.Config> anonymousPerIp,
                              RateLimiter<RedisRateLimiter.Config> anonymousGlobal) {
        this.authenticated = authenticated;
        this.anonymousPerIp = anonymousPerIp;
        this.anonymousGlobal = anonymousGlobal;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        if (!id.startsWith(ANONYMOUS_PREFIX)) {
            return authenticated.isAllowed(routeId, id);
        }
        // The global bucket is only charged once the caller's own bucket lets the request through
        return anonymousPerIp.isAllowed(routeId, id)
            .flatMap(perIp -> perIp.isAllowed() ? anonymousGlobal.isAllowed(routeId, GLOBAL_ANONYMOUS_KEY) : Mono.just(perIp));
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return Map.of();
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return RedisRateLimiter.Config.class;
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return new RedisRateLimiter.Config();
    }

    /**
     * A RedisRateLimiter allowing {@code requests} per {@code periodSeconds}, all of them usable at once.
     * RedisRateLimiter refills at least 1 token per second, so slow rates are expressed through the token
     * cost of a request: cost = period / requests tokens, capacity = requests × cost.
     */
    static RedisRateLimiter perPeriod(int requests, int periodSeconds) {
        int perRequest = tokensPerRequest(requests, periodSeconds);
        return new RedisRateLimiter(1, perRequest * Math.max(1, requests), perRequest);
    }

    static int tokensPerRequest(int requests, int periodSeconds) {
        return Math.max(1, periodSeconds / Math.max(1, requests));
    }
}
