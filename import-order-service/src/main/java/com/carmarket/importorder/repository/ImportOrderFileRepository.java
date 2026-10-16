package com.carmarket.importorder.repository;

import com.carmarket.importorder.entity.FileKind;
import com.carmarket.importorder.entity.ImportOrderFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportOrderFileRepository extends JpaRepository<ImportOrderFile, UUID> {

    List<ImportOrderFile> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<ImportOrderFile> findByOrderIdAndClientVisibleTrueOrderByCreatedAtAsc(UUID orderId);

    Optional<ImportOrderFile> findByIdAndOrderId(UUID id, UUID orderId);

    long countByOrderId(UUID orderId);

    /** Photos of a page of orders, for the list thumbnails. */
    List<ImportOrderFile> findByOrderIdInAndKindOrderByCreatedAtAsc(Collection<UUID> orderIds, FileKind kind);
}
