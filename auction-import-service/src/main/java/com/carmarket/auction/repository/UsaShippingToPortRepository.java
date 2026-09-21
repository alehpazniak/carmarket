package com.carmarket.auction.repository;

import com.carmarket.auction.entity.UsaShippingToPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsaShippingToPortRepository extends JpaRepository<UsaShippingToPort, UUID> {

    Optional<UsaShippingToPort> findByLocationIgnoreCase(String location);
}
