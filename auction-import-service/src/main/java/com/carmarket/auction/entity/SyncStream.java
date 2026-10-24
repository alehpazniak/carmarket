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
 * One feed sync stream (V8), e.g. {@code copart:open}: how far it has read (watermark) and whether the last run
 * left records behind because the budget ran out.
 */
@Entity
@Table(name = "sync_streams")
@Getter
@Setter
@NoArgsConstructor
public class SyncStream {

    @Id
    @Column(name = "stream_key", length = 100)
    private String streamKey;

    private Instant watermark;

    /** When a BACKFILL of this stream was last drained */
    @Column(name = "last_full_at")
    private Instant lastFullAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(nullable = false)
    private boolean backlog;

    /** Records left behind, from the API's total count when available */
    @Column(name = "backlog_estimate")
    private Integer backlogEstimate;

    public SyncStream(String streamKey) {
        this.streamKey = streamKey;
    }
}
