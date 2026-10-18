package com.carmarket.user.repository;

import com.carmarket.user.entity.DealerPage;
import com.carmarket.user.entity.UserProfile.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DealerPageRepository extends JpaRepository<DealerPage, UUID> {

    Optional<DealerPage> findBySlug(String slug);

    boolean existsBySlugAndUserIdNot(String slug, UUID userId);

    /**
     * Published pages with coordinates inside a bounding box, each as {@code [DealerPage, UserProfile]}, whose
     * owner is active and has one of {@code roles}. The exact distance is computed by the caller.
     */
    @Query("""
        select p, u from DealerPage p join UserProfile u on u.id = p.userId
        where p.published = true and u.active = true and u.role in :roles
          and p.latitude between :minLat and :maxLat
          and p.longitude between :minLng and :maxLng
        """)
    List<Object[]> findPublishedInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                      @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                                      @Param("roles") Collection<UserRole> roles);
}
