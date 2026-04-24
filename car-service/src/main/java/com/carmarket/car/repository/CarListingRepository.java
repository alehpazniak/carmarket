package com.carmarket.car.repository;

import com.carmarket.car.entity.CarListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CarListingRepository extends JpaRepository<CarListing, UUID> {

    Page<CarListing> findBySellerId(UUID sellerId, Pageable pageable);

    Page<CarListing> findByStatus(CarListing.ListingStatus status, Pageable pageable);
}