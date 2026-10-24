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
    private int perPage = 40;
    private List<Target> targets = new ArrayList<>();
    private VehicleTypes vehicleTypes = new VehicleTypes();

    /** Apibara Vehicle.type values the sync keeps (apibara.vehicle-types.include); see application.yml. */
    @Data
    public static class VehicleTypes {
        private List<String> include = new ArrayList<>();

        /**
         * Whether a record of this type is kept: an empty list keeps everything, and a record without a type
         * is kept too (some cars have none; a type filter in the request would never return them anyway).
         */
        public boolean includes(String type) {
            return include.isEmpty() || type == null || type.isBlank()
                || include.stream().anyMatch(t -> t.equalsIgnoreCase(type.trim()));
        }
    }

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