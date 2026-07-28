package com.carmarket.auction.parser;

import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.entity.AuctionLot.FuelType;
import com.carmarket.auction.entity.AuctionLot.LotStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Temporary mock parser — returns a fixed sample lot (Mazda CX-5) instead of
 * scraping a live auction. Replaces the Playwright-based CopartParser so the
 * service runs without a browser.
 *
 * To expand: add more lots to sampleLots(), or restore CopartParser (.bak files)
 * and re-add the Playwright dependency when live scraping is needed.
 */
@Slf4j
@Component
public class MockAuctionParser implements AuctionParser {

    @Override
    public List<AuctionLot> parseLiveLots() {
        log.info("MockAuctionParser: returning sample lots (no live scraping)");
        return sampleLots();
    }

    @Override
    public AuctionLot parseLotDetail(String lotNumber) {
        return sampleLots().stream()
            .filter(lot -> lot.getLotNumber().equals(lotNumber))
            .findFirst()
            .orElse(null);
    }

    @Override
    public boolean supports(AuctionParser.AuctionSource source) {
        return source == AuctionParser.AuctionSource.COPART;
    }

    private List<AuctionLot> sampleLots() {
        AuctionLot mazda = AuctionLot.builder()
            .vin("JM3KFBCM1J0350000")
            .make("Mazda")
            .model("CX-5")
            .year(2018)
            .source(AuctionLot.AuctionSource.COPART)
            .lotNumber("MOCK-CX5-0001")   // unique — used as upsert key
            .auctionPrice(new BigDecimal("8500.00"))
            .buyNowPrice(new BigDecimal("11000.00"))
            .currency("USD")
            .damageType("Collision")
            .primaryDamage("Front End")
            .secondaryDamage("Minor Scratches")
            .odometer(72000)
            .odometerUnit("mi")
            .engineCapacity(2500)
            .fuelType(FuelType.PETROL)
            .transmission("Automatic")
            .auctionLocation("CA")
            .auctionDate(LocalDateTime.now().plusDays(3))
            .status(LotStatus.LIVE)
            .imageUrls(List.of(
                "https://example.com/mock/mazda-cx5-front.jpg",
                "https://example.com/mock/mazda-cx5-side.jpg"))
            .rawData("{\"mock\":true,\"source\":\"sample\"}")
            .build();

        return List.of(mazda);
    }
}