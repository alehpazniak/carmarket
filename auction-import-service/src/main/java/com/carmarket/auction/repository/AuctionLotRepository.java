package com.carmarket.auction.repository;

import com.carmarket.auction.dto.LotFacetsResponse;
import com.carmarket.auction.entity.AuctionLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface  AuctionLotRepository extends JpaRepository<AuctionLot, UUID> {

    Optional<AuctionLot> findByLotNumberAndSource(String lotNumber, AuctionLot.AuctionSource source);

    /** last_seen_at for every lot of a sync page, changed or not: one UPDATE per page instead of one per row. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE AuctionLot a SET a.lastSeenAt = :now WHERE a.id IN :ids")
    int markSeen(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    List<AuctionLot> findByVin(String vin);

    /** Every platform's lot with this number (Copart and IAAI numbers can collide). */
    List<AuctionLot> findByLotNumber(String lotNumber);

    /**
     * IAAI lots whose images carry this item id (the number in an iaai.com/VehicleDetail/{id}~US link), e.g.
     * {@code %imageKeys=46069832~%}. The caller builds the pattern from digits only.
     */
    @Query(value = """
        SELECT DISTINCT a.* FROM auction_lots a JOIN auction_lot_images i ON i.lot_id = a.id
        WHERE a.source = 'IAAI' AND i.image_urls LIKE :pattern
        """, nativeQuery = true)
    List<AuctionLot> findIaaiByImageKey(@Param("pattern") String pattern);

    Page<AuctionLot> findByStatus(AuctionLot.LotStatus status, Pageable pageable);

    @Query("""
        SELECT a FROM AuctionLot a
        WHERE a.status = 'SOLD'
        AND a.make = :make
        AND a.model = :model
        AND (:year IS NULL OR a.year = :year)
        AND (:damageType IS NULL OR a.damageType = :damageType)
        """)
    List<AuctionLot> findSoldComparables(
        @Param("make") String make,
        @Param("model") String model,
        @Param("year") Integer year,
        @Param("damageType") String damageType
    );

    /**
     * Which lots the public listing shows, on alias {@code a}. Shared by {@link #searchLiveLots} and
     * {@link #findFacetRows} so the dropdown counts always match the listing.
     */
    String LISTED = "a.status = 'LIVE'";

    /**
     * {@code make}/{@code model} are exact, case-insensitive matches: the caller passes them already trimmed and
     * upper-cased (or null). Never wrap a parameter in a function here: a null parameter inside UPPER()/CONCAT()
     * is mis-typed as bytea by the Postgres driver.
     */
    @Query("SELECT a FROM AuctionLot a WHERE " + LISTED + """

        AND (:make IS NULL OR UPPER(TRIM(a.make)) = :make)
        AND (:model IS NULL OR UPPER(TRIM(a.model)) = :model)
        AND (:yearFrom IS NULL OR a.year >= :yearFrom)
        AND (:yearTo IS NULL OR a.year <= :yearTo)
        AND (:priceFrom IS NULL OR a.auctionPrice >= :priceFrom)
        AND (:priceTo IS NULL OR a.auctionPrice <= :priceTo)
        AND (:damageType IS NULL OR a.damageType = :damageType)
        """)
    Page<AuctionLot> searchLiveLots(
        @Param("make") String make,
        @Param("model") String model,
        @Param("yearFrom") Integer yearFrom,
        @Param("yearTo") Integer yearTo,
        @Param("priceFrom") BigDecimal priceFrom,
        @Param("priceTo") BigDecimal priceTo,
        @Param("damageType") String damageType,
        Pageable pageable
    );

    /** Listed lots per normalized make/model, grouped the same way {@link #searchLiveLots} matches. */
    @Query("SELECT new com.carmarket.auction.dto.LotFacetsResponse$Row(UPPER(TRIM(a.make)), UPPER(TRIM(a.model)), COUNT(a))"
        + " FROM AuctionLot a WHERE " + LISTED
        + " GROUP BY UPPER(TRIM(a.make)), UPPER(TRIM(a.model))")
    List<LotFacetsResponse.Row> findFacetRows();
}
