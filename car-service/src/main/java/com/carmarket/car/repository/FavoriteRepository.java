package com.carmarket.car.repository;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    boolean existsByUserIdAndCarId(UUID userId, UUID carId);

    long deleteByUserIdAndCarId(UUID userId, UUID carId);

    /**
     * Returns the user's favorited car listings (joined), newest-favorited first.
     * Joins favorites → car_listings so we return full car data in one query.
     */
    @Query("""
        SELECT c FROM CarListing c
        JOIN Favorite f ON f.carId = c.id
        WHERE f.userId = :userId
        ORDER BY f.createdAt DESC
        """)
    Page<CarListing> findFavoriteCars(@Param("userId") UUID userId, Pageable pageable);

    /** Of the given carIds, which ones the user has favorited — for batch "isFavorite" flags. */
    @Query("SELECT f.carId FROM Favorite f WHERE f.userId = :userId AND f.carId IN :carIds")
    List<UUID> findFavoritedCarIds(@Param("userId") UUID userId,
                                   @Param("carIds") Collection<UUID> carIds);

}
