package com.carmarket.auction.repository;

import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8TowingRate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Read-only access to W8's towing price list; every query is filtered by {@code price_list}. */
@Repository
public interface W8TowingRateRepository extends JpaRepository<W8TowingRate, Long> {

    List<W8TowingRate> findByPriceListAndAuctionAndYardKey(String priceList, W8Auction auction, String yardKey);

    List<W8TowingRate> findByPriceListAndAuctionAndYardKeyAndState(String priceList, W8Auction auction, String yardKey, String state);

    /** The same location at any auction (W8 prices by location). */
    List<W8TowingRate> findByPriceListAndYardKeyAndState(String priceList, String yardKey, String state);

    List<W8TowingRate> findByPriceListAndAuctionAndState(String priceList, W8Auction auction, String state);

    List<W8TowingRate> findByPriceListAndState(String priceList, String state);

    long countByPriceList(String priceList);

    /**
     * Autocomplete: rows whose yard key starts with {@code prefix} (already normalized), all auctions when
     * {@code auction} is null. One location has up to 6 rows (one per port), so callers group by location.
     */
    @Query("""
        select r from W8TowingRate r
        where r.priceList = :priceList
          and (:auction is null or r.auction = :auction)
          and r.yardKey like concat(:prefix, '%')
        order by r.yardKey, r.auction, r.location, r.port""")
    List<W8TowingRate> searchByYardKeyPrefix(@Param("priceList") String priceList,
                                             @Param("auction") W8Auction auction,
                                             @Param("prefix") String prefix,
                                             Pageable page);
}
