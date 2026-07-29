package com.carmarket.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Which vehicles to pull from the auction API. Add entries under
 * apibara.targets in application.yaml to expand coverage — no code changes.
 */
@Component
@ConfigurationProperties(prefix = "apibara")
@Data
public class ParserTargetsConfig {

    private String baseUrl = "https://apibara.tech";
    private String apiKey;
    private int perPage = 50;
    private List<Target> targets = new ArrayList<>();

    @Data
    public static class Target {
        private String make;
        private String model;
        private int yearFrom;
        private int yearTo;

        public boolean matchesYear(Integer year) {
            return year != null && year >= yearFrom && year <= yearTo;
        }
    }
}