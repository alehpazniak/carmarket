package com.carmarket.car.repository;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarListingRepository extends JpaRepository<CarListing, UUID> {

    Page<CarListing> findBySellerId(UUID sellerId, Pageable pageable);

    Page<CarListing> findByStatus(ListingStatus status, Pageable pageable);

    /** Row lock, so a promotion purchase, a refund and the expiry job never overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CarListing c where c.id = :id")
    Optional<CarListing> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c.id from CarListing c where c.promotedUntil <= :now or c.highlightedUntil <= :now")
    List<UUID> findIdsWithExpiredPromotion(@Param("now") LocalDateTime now);
}