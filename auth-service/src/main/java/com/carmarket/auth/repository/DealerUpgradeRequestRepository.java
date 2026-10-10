package com.carmarket.auth.repository;

import com.carmarket.auth.entity.DealerUpgradeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealerUpgradeRequestRepository extends JpaRepository<DealerUpgradeRequest, UUID> {

    Optional<DealerUpgradeRequest> findByTokenHash(String tokenHash);

    Optional<DealerUpgradeRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Unconfirmed requests stop working once a newer link is sent. */
    @Modifying
    @Query("DELETE FROM DealerUpgradeRequest r WHERE r.userId = :userId AND r.confirmedAt IS NULL")
    void deletePendingByUserId(@Param("userId") UUID userId);
}
