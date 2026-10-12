package com.carmarket.auth.repository;

import com.carmarket.auth.entity.ProviderType;
import com.carmarket.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /**
     * Active admins, row-locked: the last-admin guard runs under this lock, so two admins can't
     * demote or block each other at the same time and leave nobody.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = com.carmarket.auth.entity.User$UserRole.ADMIN AND u.active = true")
    List<User> lockActiveAdmins();

    @Query("SELECT u.role, count(u) FROM User u GROUP BY u.role")
    List<Object[]> countByRole();

    long countByActiveFalse();

    long countByCreatedAtAfter(LocalDateTime after);

    @Query(value = """
        SELECT CAST(date_trunc('day', created_at) AS date) AS day, count(*) AS cnt
        FROM users WHERE created_at >= :from GROUP BY 1 ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> dailyRegistrations(@Param("from") LocalDateTime from);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.providers p " +
        "WHERE p.provider = :provider AND p.providerUserId = :providerUserId")
    Optional<User> findByProviderAndProviderId(
        @Param("provider") ProviderType provider,
        @Param("providerUserId") String providerUserId);
}
