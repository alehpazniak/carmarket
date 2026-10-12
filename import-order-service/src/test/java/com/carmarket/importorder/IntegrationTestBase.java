package com.carmarket.importorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole service against a throwaway Postgres (Flyway runs the real migrations), so Docker
 * must be running. One container is shared by every test class; S3 is mocked per class.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
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

    /** Default gateway.internal-secret from application.yml. */
    private static final String GATEWAY_SECRET = "bXlTdXBlclNlY3JldEtleUZvckNhck1hcmtldEp3dFRva2VuMjAyNA==";

    protected static final UUID DEALER_A = UUID.randomUUID();
    protected static final UUID DEALER_B = UUID.randomUUID();
    protected static final UUID ADMIN = UUID.randomUUID();
    protected static final UUID PLAIN_USER = UUID.randomUUID();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    /** No broker in tests; PR-3 tests verify what would be sent. */
    @MockBean
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE import_orders CASCADE");
    }

    /** A request as it arrives from the gateway: identity headers plus a fresh signature. */
    protected static RequestPostProcessor as(UUID userId, String roles) {
        return request -> {
            request.addHeader("X-User-Id", userId.toString());
            request.addHeader("X-User-Roles", roles);
            request.addHeader("X-Gateway-Signature", sign(userId.toString()));
            return request;
        };
    }

    protected static RequestPostProcessor dealerA() {
        return as(DEALER_A, "DEALER");
    }

    protected static RequestPostProcessor dealerB() {
        return as(DEALER_B, "DEALER");
    }

    protected static RequestPostProcessor admin() {
        return as(ADMIN, "ADMIN");
    }

    protected static RequestPostProcessor plainUser() {
        return as(PLAIN_USER, "USER");
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

    protected record Created(UUID id, String code, String clientLink, String password, JsonNode body) {
    }

    protected Created createOrder(RequestPostProcessor who, Map<String, Object> body) throws Exception {
        MvcResult result = mvc.perform(post("/import-orders").with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        String link = node.get("clientLink").asText();
        return new Created(UUID.fromString(node.get("order").get("id").asText()),
            link.substring(link.lastIndexOf('/') + 1), link, node.get("password").asText(), node);
    }

    protected Created createOrder(RequestPostProcessor who) throws Exception {
        return createOrder(who, Map.of("source", "COPART", "lotNumber", UUID.randomUUID().toString().substring(0, 8),
            "make", "Audi", "model", "Q5", "year", 2019));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
