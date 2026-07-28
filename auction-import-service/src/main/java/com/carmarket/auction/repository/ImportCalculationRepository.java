package com.carmarket.auction.repository;

import com.carmarket.auction.entity.ImportCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportCalculationRepository extends JpaRepository<ImportCalculation, UUID> {

    @Query("""
        SELECT ic FROM ImportCalculation ic
        JOIN FETCH ic.lot l
        WHERE l.status = 'SOLD'
        AND ic.estimatedProfitPln IS NOT NULL
        """)
    List<ImportCalculation> findAllWithSoldLots();
}
