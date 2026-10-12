package com.carmarket.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void withoutTrustedProxiesAFakeForwardedForIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(0);
        KeyResolver anonymous = new GatewayConfig().keyResolver(resolver);
        KeyResolver access = GatewayConfig.importAccessKeyResolver(resolver);

        ServerWebExchange first = exchange("1.1.1.1");
        ServerWebExchange second = exchange("2.2.2.2, 3.3.3.3");

        assertThat(anonymous.resolve(first).block()).isEqualTo("203.0.113.9")
            .isEqualTo(anonymous.resolve(second).block());
        assertThat(access.resolve(first).block()).isEqualTo("import-access:203.0.113.9")
            .isEqualTo(access.resolve(second).block());
    }

    @Test
    void withOneTrustedProxyTheLastForwardedForEntryIsTheClient() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(exchange("1.2.3.4, 198.51.100.23"))).isEqualTo("198.51.100.23");
        assertThat(GatewayConfig.importAccessKeyResolver(resolver).resolve(exchange("198.51.100.23")).block())
            .isEqualTo("import-access:198.51.100.23");
    }

    @Test
    void withTwoTrustedProxiesTheSecondEntryFromTheRightIsTheClient() {
        assertThat(new ClientIpResolver(2).resolve(exchange("6.6.6.6, 198.51.100.23, 10.0.0.2")))
            .isEqualTo("198.51.100.23");
    }

    @Test
    void withTrustedProxiesButNoForwardedForFallsBackToTheRemoteAddress() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .remoteAddress(new InetSocketAddress("203.0.113.9", 4000)));

        assertThat(new ClientIpResolver(1).resolve(exchange)).isEqualTo("203.0.113.9");
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new ClientIpResolver(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ServerWebExchange exchange(String forwardedFor) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/import-orders/public/abc/access")
            .remoteAddress(new InetSocketAddress("203.0.113.9", 4000))
            .header("X-Forwarded-For", forwardedFor));
    }
}
