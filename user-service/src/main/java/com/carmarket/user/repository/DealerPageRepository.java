package com.carmarket.user.repository;

import com.carmarket.user.entity.DealerPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealerPageRepository extends JpaRepository<DealerPage, UUID> {

    Optional<DealerPage> findBySlug(String slug);

    boolean existsBySlugAndUserIdNot(String slug, UUID userId);
}
