package com.carmarket.gateway.filter;

import com.carmarket.gateway.security.GatewaySignatureService;
import com.carmarket.gateway.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void importOrderClientPageIsPublicAndStripsSpoofedIdentity() {
        for (MockServerHttpRequest request : List.of(
            MockServerHttpRequest.post("/api/import-orders/public/abc/access").header("X-User-Id", "spoofed").build(),
            MockServerHttpRequest.get("/api/import-orders/public/abc").header("X-User-Id", "spoofed").build(),
            MockServerHttpRequest.get("/api/import-orders/public/abc/files/def/url").build())) {
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

            filter.filter(exchange, ex -> {
                forwarded.set(ex);
                return Mono.empty();
            }).block();

            assertThat(forwarded.get()).as(request.getPath().value()).isNotNull();
            assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
        }
    }

    @Test
    void importOrderDealerRoutesRequireAToken() {
        for (MockServerHttpRequest request : List.of(
            MockServerHttpRequest.get("/api/import-orders").build(),
            MockServerHttpRequest.get("/api/import-orders/123").build(),
            MockServerHttpRequest.post("/api/import-orders/123/files").build(),
            MockServerHttpRequest.delete("/api/import-orders/public/abc").build())) {
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            AtomicBoolean forwarded = new AtomicBoolean();

            filter.filter(exchange, chain(forwarded)).block();

            assertThat(exchange.getResponse().getStatusCode()).as(request.getPath().value()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(forwarded).isFalse();
        }
    }

    private static GatewayFilterChain chain(AtomicBoolean forwarded) {
        return ex -> {
            forwarded.set(true);
            return Mono.empty();
        };
    }
}
