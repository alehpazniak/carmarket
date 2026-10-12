package com.carmarket.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(SystemHealth system) {

    /** healthTimeout: per-instance /actuator/health call; expectedServices: reported DOWN when not registered. */
    public record SystemHealth(Duration healthTimeout, List<String> expectedServices) {
    }
}
