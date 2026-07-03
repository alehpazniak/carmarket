package com.carmarket.car.repository;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CarListingRepository extends JpaRepository<CarListing, UUID> {

    Page<CarListing> findBySellerId(UUID sellerId, Pageable pageable);

    Page<CarListing> findByStatus(ListingStatus status, Pageable pageable);
}