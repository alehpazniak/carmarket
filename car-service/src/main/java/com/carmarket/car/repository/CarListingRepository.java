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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarListingRepository extends JpaRepository<CarListing, UUID> {

    Page<CarListing> findBySellerId(UUID sellerId, Pageable pageable);

    Page<CarListing> findByStatus(ListingStatus status, Pageable pageable);

    List<CarListing> findBySellerIdAndStatus(UUID sellerId, ListingStatus status);

    /** Row lock, so a promotion purchase, a refund and the expiry job never overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CarListing c where c.id = :id")
    Optional<CarListing> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c.id from CarListing c where c.promotedUntil <= :now or c.highlightedUntil <= :now")
    List<UUID> findIdsWithExpiredPromotion(@Param("now") LocalDateTime now);

    /** Columns read by {@link #findSummariesByIdIn}. */
    interface SummaryView {
        UUID getId();
        String getMake();
        String getModel();
        Integer getYear();
        BigDecimal getPrice();
        String getPrimaryImageUrl();
        String getStatus();
    }

    /**
     * Native on purpose: JPQL would apply the entity's {@code @SQLRestriction} and hide REMOVED
     * listings, which callers labelling old conversations still need. Quoted aliases keep the
     * camelCase names Postgres would otherwise lower-case.
     */
    @Query(value = """
        select id as "id", make as "make", model as "model", year as "year", price as "price",
               primary_image_url as "primaryImageUrl", status as "status"
        from car_listings where id in (:ids)
        """, nativeQuery = true)
    List<SummaryView> findSummariesByIdIn(@Param("ids") Collection<UUID> ids);
}