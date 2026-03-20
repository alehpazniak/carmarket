package com.carmarket.auth.repository;

import com.carmarket.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.providers p " +
        "WHERE p.provider = :provider AND p.providerUserId = :providerUserId")
    Optional<User> findByProviderAndProviderId(
        @Param("provider") com.carmarket.auth.entity.OAuthProvider.ProviderType provider,
        @Param("providerUserId") String providerUserId);
}
