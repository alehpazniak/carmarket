package com.carmarket.auction.repository;

import com.carmarket.auction.entity.AuctionLot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionLotRepository extends JpaRepository<AuctionLot, UUID> {

    Optional<AuctionLot> findByLotNumberAndSource(String lotNumber, AuctionLot.AuctionSource source);

    List<AuctionLot> findByVin(String vin);

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

    @Query("""
        SELECT a FROM AuctionLot a
        WHERE a.status = 'LIVE'
        AND (:make IS NULL OR a.make = :make)
        AND (:model IS NULL OR LOWER(a.model) LIKE LOWER(CONCAT('%', :model, '%')))
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
}
