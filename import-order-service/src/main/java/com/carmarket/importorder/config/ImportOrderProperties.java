package com.carmarket.importorder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "import-order")
public record ImportOrderProperties(
    String frontendUrl,
    Password password,
    Access access,
    Token token,
    S3 s3
) {

    public record Password(int bcryptCost) {
    }

    public record Access(int maxFailedAttempts, Duration lockDuration) {
    }

    public record Token(String secret, Duration ttl) {
    }

    public record S3(String bucket, String region, String accessKeyId, String secretAccessKey, Duration presignTtl) {

        /** All four values set; otherwise storage is disabled and file endpoints answer 503. */
        public boolean isConfigured() {
            return notBlank(bucket) && notBlank(region) && notBlank(accessKeyId) && notBlank(secretAccessKey);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** Client page link for a public code. */
    public String clientLink(String publicCode) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/zlecenie/" + publicCode;
    }
}
