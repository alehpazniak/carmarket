package com.carmarket.auction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The Apibara feed sync (apibara.sync in application.yml; docs/tasks/auction-feed-rollout.md, Sizing).
 * Scheduled runs start at {@link #dailyStart} and then every {@link #runEveryMinutes} until the end of the active
 * hours; 0 = one run a day. A run spends at most the day's share of {@link #monthlyBudget}.
 */
@Component
@ConfigurationProperties(prefix = "apibara.sync")
@Data
public class ApibaraSyncProperties {

    /** Off by default: an IDE restart must not spend quota */
    private boolean enabled = false;
    private ZoneId zone = ZoneId.of("Europe/Warsaw");
    /** "HH:mm-HH:mm"; no scheduled run starts outside it */
    private String activeHours = "07:00-23:00";
    private LocalTime dailyStart = LocalTime.of(8, 0);
    /** 0 = only the run at dailyStart */
    private int runEveryMinutes = 0;
    /** Requests the sync may spend per subscription period (X-Subscription-Period-*, else the calendar month) */
    private int monthlyBudget = 30;
    /** Never let X-Subscription-Remaining drop below this (live lookups and lot pages need the rest) */
    private int keepRemaining = 15;
    /** 0 = today only, 1 = today and tomorrow, ... */
    private int daysAhead = 0;
    private RefreshHours refreshHours = new RefreshHours();
    /** year_from for the day passes; null = no filter */
    private Integer yearFrom;
    /** GET /changes page size (Apibara's maximum is 500) */
    private int changesLimit = 500;

    /** How old a day's last complete pass may be before it is read again */
    @Data
    public static class RefreshHours {
        private int today = 4;
        private int tomorrow = 12;
        private int later = 24;

        /** 0 = today, 1 = tomorrow, 2+ = later */
        public int forDay(long daysFromToday) {
            return daysFromToday <= 0 ? today : daysFromToday == 1 ? tomorrow : later;
        }
    }

    public LocalTime activeFrom() {
        return LocalTime.parse(activeHours.split("-")[0].trim());
    }

    public LocalTime activeTo() {
        return LocalTime.parse(activeHours.split("-")[1].trim());
    }

    public boolean isActive(LocalTime time) {
        return !time.isBefore(activeFrom()) && time.isBefore(activeTo());
    }
}
