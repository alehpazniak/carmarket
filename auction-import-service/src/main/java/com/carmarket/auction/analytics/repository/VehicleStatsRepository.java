package com.carmarket.auction.analytics.repository;

import com.carmarket.auction.analytics.entity.VehicleStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleStatsRepository extends JpaRepository<VehicleStats, java.util.UUID> {

    Optional<VehicleStats> findByMakeAndModelAndYearAndDamageType(
        String make, String model, Integer year, String damageType);

    List<VehicleStats> findByMakeAndModelOrderByYearDesc(String make, String model);

    @Query("""
        SELECT v FROM VehicleStats v
        WHERE v.make = :make
        AND v.avgProfit > 0
        ORDER BY v.avgProfit DESC
        """)
    List<VehicleStats> findMostProfitableByMake(String make);
}
