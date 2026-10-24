package com.carmarket.auction.repository;

import com.carmarket.auction.entity.SyncStream;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStreamRepository extends JpaRepository<SyncStream, String> {
}
