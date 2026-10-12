package com.carmarket.car.service;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Follows the seller's account state: a blocked account's ACTIVE listings become SUSPENDED (hidden
 * from search and the public page), and unblocking restores exactly those — SOLD, DRAFT or
 * moderation-BLOCKED listings are never touched. car.updated goes out after commit for each one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerAccountService {

    private final CarListingRepository repository;
    private final CarEventProducer eventProducer;

    @Transactional
    public int suspendListings(UUID sellerId) {
        return move(sellerId, ListingStatus.ACTIVE, ListingStatus.SUSPENDED);
    }

    @Transactional
    public int restoreListings(UUID sellerId) {
        return move(sellerId, ListingStatus.SUSPENDED, ListingStatus.ACTIVE);
    }

    private int move(UUID sellerId, ListingStatus from, ListingStatus to) {
        List<CarListing> listings = repository.findBySellerIdAndStatus(sellerId, from);
        for (CarListing car : listings) {
            car.setStatus(to);
            eventProducer.publishUpdated(repository.save(car));
        }
        log.info("Seller {}: {} listing(s) {} -> {}", sellerId, listings.size(), from, to);
        return listings.size();
    }
}
