package com.carmarket.car.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** Injected instead of calling LocalDateTime.now() directly, so promotion timing is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
