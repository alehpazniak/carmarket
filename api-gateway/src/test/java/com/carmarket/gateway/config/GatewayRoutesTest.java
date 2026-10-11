package com.carmarket.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the gateway context without Eureka; Redis is only contacted when a limiter is used. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void phoneLookupsHitTheDedicatedRouteBeforeTheGenericUserRoute() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        Route matched = routes.stream()
            .filter(r -> matches(r, "/api/users/abc/phone?carId=x"))
            .findFirst().orElseThrow();
        assertThat(matched.getId()).isEqualTo("user-service-phone");

        Route profile = routes.stream()
            .filter(r -> matches(r, "/api/users/abc"))
            .findFirst().orElseThrow();
        assertThat(profile.getId()).isEqualTo("user-service");
    }

    @Test
    void phoneKeyResolverUsesItsOwnBucketPerUser() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users/abc/phone").header("X-User-Id", "u1"));

        assertThat(GatewayConfig.PHONE_KEY_RESOLVER.resolve(exchange).block()).isEqualTo("phone:u1");
        assertThat(GatewayConfig.PHONE_KEY_RESOLVER.resolve(exchange("/api/users/abc/phone")).blockOptional()).isEmpty();
    }

    private static boolean matches(Route route, String uri) {
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange(uri))).block());
    }

    private static ServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri));
    }
}
