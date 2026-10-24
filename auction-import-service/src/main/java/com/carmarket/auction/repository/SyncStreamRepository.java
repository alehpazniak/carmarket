package com.carmarket.auction.repository;

import com.carmarket.auction.entity.SyncStream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SyncStreamRepository extends JpaRepository<SyncStream, String> {

    /** Day streams, oldest day first (keys are "open:yyyy-MM-dd", so they sort by date). */
    @Query("SELECT s FROM SyncStream s WHERE s.streamKey LIKE 'open:%' ORDER BY s.streamKey")
    List<SyncStream> findDays();

    /** Drops the rows of days before {@code oldestKept} ("open:yyyy-MM-dd"). */
    @Transactional
    @Modifying
    @Query("DELETE FROM SyncStream s WHERE s.streamKey LIKE 'open:%' AND s.streamKey < :oldestKept")
    int deleteDaysBefore(@Param("oldestKept") String oldestKept);
}
