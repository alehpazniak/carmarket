package com.carmarket.auth.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests only: application.yml and docker-compose.yml have no secret defaults (the service refuses to start without
 * them), and tests must not depend on a developer's .env. Every name gets a fresh random value per test JVM, so no
 * secret literal sits in the repo. Lowest precedence: a test that needs a known value sets the property itself
 * (e.g. gateway.internal-secret). Registered in src/test/resources/META-INF/spring.factories; a copy per service,
 * like the rest of the cross-cutting code.
 */
public class RandomTestSecrets implements EnvironmentPostProcessor {

    static final List<String> NAMES = List.of("JWT_SECRET", "GATEWAY_INTERNAL_SECRET", "IMPORT_ORDER_TOKEN_SECRET",
        "DB_PASSWORD", "CHAT_DB_PASSWORD", "NOTIFICATION_DB_PASSWORD", "EUREKA_PASSWORD", "AWS_ACCESS_KEY_ID",
        "AWS_SECRET_ACCESS_KEY", "APIBARA_API_KEY", "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : NAMES) {
            byte[] bytes = new byte[48];
            RANDOM.nextBytes(bytes);
            values.put(name, Base64.getEncoder().encodeToString(bytes)); // base64, >= 32 bytes: valid as a JWT key too
        }
        environment.getPropertySources().addLast(new MapPropertySource("randomTestSecrets", values));
    }
}
