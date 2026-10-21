package com.carmarket.auction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A parser run of LotSyncScheduler; finishedAt is null while it runs, error is set when it failed. */
@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class SyncRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String parser;

    /** SCHEDULED or MANUAL */
    @Column(nullable = false, length = 20)
    private String trigger;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "lots_fetched")
    private Integer lotsFetched;

    @Column(name = "lots_added")
    private Integer lotsAdded;

    @Column(name = "lots_updated")
    private Integer lotsUpdated;

    @Column(length = 1000)
    private String error;

    /** Requests this run sent to Apibara, failed ones included (V7) */
    @Column(name = "api_requests")
    private Integer apiRequests;

    /** The last quota headers this run saw (ApibaraQuota); null when it got no answer */
    @Column(name = "quota_limit")
    private Integer quotaLimit;

    @Column(name = "quota_used")
    private Integer quotaUsed;

    @Column(name = "quota_remaining")
    private Integer quotaRemaining;

    @Column(name = "quota_period_start")
    private Instant quotaPeriodStart;

    @Column(name = "quota_period_end")
    private Instant quotaPeriodEnd;

    @Column(name = "quota_observed_at")
    private Instant quotaObservedAt;

    public static SyncRun start(String parser, String trigger) {
        SyncRun run = new SyncRun();
        run.id = UUID.randomUUID();
        run.parser = parser;
        run.trigger = trigger;
        run.startedAt = Instant.now();
        return run;
    }
}
