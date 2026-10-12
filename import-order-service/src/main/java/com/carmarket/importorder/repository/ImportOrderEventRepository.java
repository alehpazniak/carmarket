package com.carmarket.importorder.repository;

import com.carmarket.importorder.entity.ImportOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportOrderEventRepository extends JpaRepository<ImportOrderEvent, UUID> {

    /** Timeline, newest first; the first element is the current status. */
    List<ImportOrderEvent> findByOrderIdOrderByOccurredAtDescCreatedAtDesc(UUID orderId);

    Optional<ImportOrderEvent> findFirstByOrderIdOrderByOccurredAtDescCreatedAtDesc(UUID orderId);

    Optional<ImportOrderEvent> findByIdAndOrderId(UUID id, UUID orderId);
}
