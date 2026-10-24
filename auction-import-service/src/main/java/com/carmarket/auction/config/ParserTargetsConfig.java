package com.carmarket.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Apibara connection and which vehicle types the sync keeps (apibara.* in application.yml).
 * The sync's schedule and budget are in {@link ApibaraSyncProperties} (apibara.sync.*).
 */
@Component
@ConfigurationProperties(prefix = "apibara")
@Data
public class ParserTargetsConfig {

    private String baseUrl = "https://apibara.tech";
    private String apiKey;
    private int perPage = 20;
    private VehicleTypes vehicleTypes = new VehicleTypes();
    private Filters filters = new Filters();

    /**
     * Which lots the sync keeps beyond their type (apibara.filters): Run and Drive only, and no title from the
     * exclude list. Titles are matched case-insensitively by substring, since they vary and carry a state, e.g.
     * "CERTIFICATE OF DESTRUCTION (FL)". An exclude list, not an allow list: the API can't filter titles that way.
     */
    @Data
    public static class Filters {
        private boolean runAndDriveOnly = true;
        private List<String> titleExclude = new ArrayList<>();

        /**
         * Copart's "Run and Drive" (and "Run & Drive Verified"), IAAI's "Run & Drive": Apibara's value then
         * contains RUN and DRIVE, e.g. "RUNS AND DRIVES". Not "ENGINE START PROGRAM", "STATIONARY",
         * "NO INFORMATION"; an unknown condition doesn't qualify either.
         */
        public boolean acceptsRunCondition(String value) {
            if (!runAndDriveOnly) return true;
            if (value == null) return false;
            String upper = value.toUpperCase(java.util.Locale.ROOT);
            return upper.contains("RUN") && upper.contains("DRIVE");
        }

        /** False when the title contains an excluded entry; a lot without a title is kept (nothing to judge). */
        public boolean acceptsTitle(String title) {
            if (title == null || title.isBlank()) return true;
            String upper = title.toUpperCase(java.util.Locale.ROOT);
            return titleExclude.stream().noneMatch(t -> !t.isBlank() && upper.contains(t.trim().toUpperCase(java.util.Locale.ROOT)));
        }
    }

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
}
