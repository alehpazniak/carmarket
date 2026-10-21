package com.carmarket.auction.repository;

import com.carmarket.auction.entity.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    /** The latest run of every parser. */
    @Query(value = "SELECT DISTINCT ON (parser) * FROM sync_runs ORDER BY parser, started_at DESC", nativeQuery = true)
    List<SyncRun> findLatestPerParser();

    /** The most recent persisted Apibara quota snapshot (ApibaraQuotaTracker after a restart). */
    Optional<SyncRun> findFirstByQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc();
}
