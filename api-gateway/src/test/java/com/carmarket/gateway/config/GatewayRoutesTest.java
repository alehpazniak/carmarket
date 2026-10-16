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

import java.net.InetSocketAddress;
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

    @Test
    void dealerPagePhoneHitsItsOwnRouteAndPagesTheGenericOne() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/users/dealers/auto-kowalski/phone")))
            .isEqualTo("user-service-dealer-phone");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/users/dealers/auto-kowalski")))
            .isEqualTo("user-service");
    }

    @Test
    void dealerPhoneKeyResolverUsesItsOwnBucketPerIp() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/dealers/x/phone")
            .remoteAddress(new InetSocketAddress("203.0.113.9", 4000)));

        assertThat(GatewayConfig.dealerPhoneKeyResolver(new ClientIpResolver(0)).resolve(exchange).block())
            .isEqualTo("dealer-phone:203.0.113.9");
    }

    @Test
    void importOrderPasswordChecksHitTheStrictRouteFirst() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(firstMatch(routes, MockServerHttpRequest.post("/api/import-orders/public/abc/access")))
            .isEqualTo("import-order-service-access");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/import-orders/public/abc")))
            .isEqualTo("import-order-service");
        assertThat(firstMatch(routes, MockServerHttpRequest.post("/api/import-orders/123/files")))
            .isEqualTo("import-order-service-files");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/import-orders/123/files/456/url")))
            .isEqualTo("import-order-service");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/import-orders")))
            .isEqualTo("import-order-service-root");
    }

    @Test
    void importAccessKeyResolverUsesItsOwnBucketPerIp() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/import-orders/public/abc/access")
            .remoteAddress(new InetSocketAddress("203.0.113.9", 4000)));

        assertThat(GatewayConfig.importAccessKeyResolver(new ClientIpResolver(0)).resolve(exchange).block()).isEqualTo("import-access:203.0.113.9");
    }

    @Test
    void adminPanelRoutesToAdminService() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/admin/audit"))).isEqualTo("admin-service");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/admin/system/health"))).isEqualTo("admin-service");
        assertThat(firstMatch(routes, MockServerHttpRequest.get("/api/notifications/admin/stats"))).isEqualTo("notification-service-admin");
        assertThat(routes.stream().filter(r -> matches(r, "/api/notifications/abc")).findFirst()).isEmpty();
    }

    private static String firstMatch(List<Route> routes, MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerHttpRequest built = request.build();
        return routes.stream()
            .filter(r -> Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(MockServerWebExchange.from(built))).block()))
            .findFirst().orElseThrow().getId();
    }

    private static boolean matches(Route route, String uri) {
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange(uri))).block());
    }

    private static ServerWebExchange exchange(String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(uri));
    }
}
