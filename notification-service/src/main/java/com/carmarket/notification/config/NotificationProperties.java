package com.carmarket.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    String mailFrom,
    Duration chatMessageDelay,
    int batchSize,
    int maxAttempts,
    /** Updates to one import order within this window go out as a single email. */
    Duration importOrderUpdateDelay
) {
}
