package com.carmarket.importorder.repository;

import com.carmarket.importorder.entity.AccessEvent;
import com.carmarket.importorder.entity.AccessLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLogEntry, UUID> {

    List<AccessLogEntry> findByOrderIdOrderByAtDesc(UUID orderId, Pageable pageable);

    boolean existsByOrderIdAndTokenIdAndEventAndAtAfter(UUID orderId, String tokenId, AccessEvent event, Instant after);
}
