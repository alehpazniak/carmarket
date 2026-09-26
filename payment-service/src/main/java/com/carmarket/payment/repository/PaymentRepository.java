package com.carmarket.payment.repository;

import com.carmarket.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Row lock so a P24 notification retry and a manual sync can't both mark the payment paid. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.sessionId = :sessionId")
    Optional<Payment> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
