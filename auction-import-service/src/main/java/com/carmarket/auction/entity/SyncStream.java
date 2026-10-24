package com.carmarket.auction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One feed sync stream (V8, V9): {@code outcomes} (GET /changes, position in {@link #sequence}) or one auction day,
 * {@code open:2026-11-03} (GET /vehicles for that day, an unfinished pass resumes from {@link #cursor}).
 */
@Entity
@Table(name = "sync_streams")
@Getter
@Setter
@NoArgsConstructor
public class SyncStream {

    public static final String OUTCOMES = "outcomes";
    public static final String DAY_PREFIX = "open:";

    @Id
    @Column(name = "stream_key", length = 100)
    private String streamKey;

    /** Day streams: when the current (or last) pass started; it becomes lastFullAt once the pass is complete */
    private Instant watermark;

    /** When the last complete pass of this day started */
    @Column(name = "last_full_at")
    private Instant lastFullAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** True while a pass is unfinished, or outcomes are behind the latest sequence */
    @Column(nullable = false)
    private boolean backlog;

    /** Records left behind: lots of an unfinished pass, or outcomes events not read yet */
    @Column(name = "backlog_estimate")
    private Integer backlogEstimate;

    /** next_cursor of an unfinished day pass; null when no pass is in progress */
    @Column(columnDefinition = "TEXT")
    private String cursor;

    /** meta.total of the day when its current / last pass started */
    @Column(name = "last_total")
    private Integer lastTotal;

    @Column(name = "pages_done")
    private Integer pagesDone;

    /** outcomes: the last /changes sequence whose events were applied and committed */
    private Long sequence;

    public SyncStream(String streamKey) {
        this.streamKey = streamKey;
    }
}
