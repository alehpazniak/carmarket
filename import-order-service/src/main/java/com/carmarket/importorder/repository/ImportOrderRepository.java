package com.carmarket.importorder.repository;

import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.LotSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ImportOrderRepository extends JpaRepository<ImportOrder, UUID>, JpaSpecificationExecutor<ImportOrder> {

    Optional<ImportOrder> findByIdAndDealerId(UUID id, UUID dealerId);

    Optional<ImportOrder> findByPublicCode(String publicCode);

    /** Row lock for the password check, so concurrent wrong guesses can't skip the failure counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ImportOrder o where o.publicCode = :code")
    Optional<ImportOrder> findByPublicCodeForUpdate(@Param("code") String code);

    boolean existsByPublicCode(String publicCode);

    boolean existsByDealerIdAndSourceAndLotNumber(UUID dealerId, LotSource source, String lotNumber);

    boolean existsByDealerIdAndSourceAndLotNumberAndIdNot(UUID dealerId, LotSource source, String lotNumber, UUID id);
}
