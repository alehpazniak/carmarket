package com.carmarket.car.repository;

import com.carmarket.car.entity.ListingPromotionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ListingPromotionPaymentRepository extends JpaRepository<ListingPromotionPayment, UUID> {
}
