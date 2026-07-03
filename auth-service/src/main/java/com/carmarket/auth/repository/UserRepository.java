package com.carmarket.auth.repository;

import com.carmarket.auth.entity.User;
import com.carmarket.auth.entity.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.providers p " +
        "WHERE p.provider = :provider AND p.providerUserId = :providerUserId")
    Optional<User> findByProviderAndProviderId(
        @Param("provider") ProviderType provider,
        @Param("providerUserId") String providerUserId);
}
