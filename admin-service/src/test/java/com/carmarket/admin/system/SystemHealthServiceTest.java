package com.carmarket.admin.system;

import com.carmarket.admin.config.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHealthServiceTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/up/actuator/health", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        server.createContext("/down/actuator/health", ex -> reply(ex, 503, "{\"status\":\"DOWN\"}"));
        server.createContext("/slow/actuator/health", ex -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, "{\"status\":\"UP\"}");
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ServiceInstance instance(String service, String path) {
        return new DefaultServiceInstance(service + "-1", service, "127.0.0.1", port, false, Map.of()) {
            @Override
            public java.net.URI getUri() {
                return java.net.URI.create("http://127.0.0.1:" + port + "/" + path);
            }
        };
    }

    private SystemHealthService service(Map<String, List<ServiceInstance>> registry, List<String> expected, Duration timeout) {
        DiscoveryClient discovery = new DiscoveryClient() {
            @Override
            public String description() {
                return "test";
            }

            @Override
            public List<ServiceInstance> getInstances(String serviceId) {
                return registry.getOrDefault(serviceId, List.of());
            }

            @Override
            public List<String> getServices() {
                return List.copyOf(registry.keySet());
            }
        };
        return new SystemHealthService(discovery, new ObjectMapper(),
            new AdminProperties(new AdminProperties.SystemHealth(timeout, expected)));
    }

    @Test
    void reportsUpDownAndMissingServices() {
        SystemHealthService health = service(Map.of(
                "car-service", List.of(instance("car-service", "up")),
                "chat-service", List.of(instance("chat-service", "down"))),
            List.of("car-service", "chat-service", "notification-service"), Duration.ofSeconds(2));

        SystemHealthService.SystemHealth result = health.check();

        assertThat(result.overall()).isEqualTo("DEGRADED");
        assertThat(result.instances()).extracting(SystemHealthService.InstanceHealth::service,
                SystemHealthService.InstanceHealth::status)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("car-service", "UP"),
                org.assertj.core.groups.Tuple.tuple("chat-service", "DOWN"),
                org.assertj.core.groups.Tuple.tuple("notification-service", "DOWN"));
        assertThat(result.instances().get(0).latencyMs()).isNotNull();
        assertThat(result.instances().get(2).error()).isEqualTo("No registered instance");
    }

    @Test
    void anUnreachableOrSlowInstanceIsDownWithinTheTimeout() {
        ServiceInstance dead = new DefaultServiceInstance("pay-1", "payment-service", "127.0.0.1", 1, false);
        SystemHealthService health = service(Map.of(
                "payment-service", List.of(dead),
                "search-service", List.of(instance("search-service", "slow"))),
            List.of(), Duration.ofMillis(500));

        long start = System.currentTimeMillis();
        SystemHealthService.SystemHealth result = health.check();

        assertThat(System.currentTimeMillis() - start).isLessThan(1400);
        assertThat(result.instances()).allMatch(h -> "DOWN".equals(h.status()));
    }

    @Test
    void allUpIsUp() {
        SystemHealthService health = service(Map.of("car-service", List.of(instance("car-service", "up"))),
            List.of("car-service"), Duration.ofSeconds(2));

        assertThat(health.check().overall()).isEqualTo("UP");
    }
}
