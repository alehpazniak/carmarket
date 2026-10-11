package com.carmarket.gateway.filter;

import com.carmarket.gateway.security.GatewaySignatureService;
import com.carmarket.gateway.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(mock(JwtUtil.class), mock(GatewaySignatureService.class));

    @Test
    void carSummariesRequireAToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/cars/summaries?ids=x"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void singleListingStaysPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/cars/abc"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded).isTrue();
    }

    private static GatewayFilterChain chain(AtomicBoolean forwarded) {
        return ex -> {
            forwarded.set(true);
            return Mono.empty();
        };
    }
}
