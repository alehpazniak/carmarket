package com.carmarket.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Boots the whole service against a throwaway Postgres (Flyway runs the real migrations), so Docker
 * must be running. No Kafka broker: the listener is not started and tests call the consumer directly.
 */
@SpringBootTest(properties = {
    "gateway.internal-secret=" + IntegrationTestBase.GATEWAY_SECRET,
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.kafka.listener.auto-startup=false",
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** gateway.internal-secret for these tests (application.yml has no default; set below). */
    static final String GATEWAY_SECRET = "test-gateway-signature-secret-for-integration-tests";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** A request as it arrives from the gateway: identity headers plus a fresh signature. */
    protected static RequestPostProcessor as(UUID userId, String roles) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Roles", roles);
            request.addHeader("X-Gateway-Signature", sign(userId.toString()));
            return request;
        };
    }

    protected static RequestPostProcessor admin() {
        return as(UUID.randomUUID(), "ADMIN");
    }

    protected static RequestPostProcessor plainUser() {
        return as(UUID.randomUUID(), "USER");
    }

    private static String sign(String userId) {
        try {
            long ts = System.currentTimeMillis();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(GATEWAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((userId + ":" + ts).getBytes(StandardCharsets.UTF_8));
            return ts + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
