package com.carmarket.auction.parser;//package com.carmarket.auction.parser;
//
//import com.carmarket.auction.entity.AuctionLot;
//import com.microsoft.playwright.*;
//import com.microsoft.playwright.options.WaitUntilState;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class CopartParser implements AuctionParser {
//
//    private final PlaywrightConfig playwrightConfig;
//    private static final String SEARCH_URL = "https://www.copart.com/lotSearchResults/?free=true&query=";
//    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([0-9,]+\\.?\\d{0,2})");
//
//    @Override
//    public boolean supports(AuctionSource source) {
//        return source == AuctionSource.COPART;
//    }
//
//    @Override
//    public List<AuctionLot> parseLiveLots() {
//        List<AuctionLot> lots = new ArrayList<>();
//
//        try (Playwright playwright = Playwright.create()) {
//            BrowserContext context = playwrightConfig.createContext(playwright);
//            Page page = context.newPage();
//
//            // Navigate with retry logic
//            page.navigate(SEARCH_URL, new Page.NavigateOptions()
//                .setTimeout(60000)
//                .setWaitUntil(WaitUntilState.NETWORKIDLE));
//
//            // Wait for results to load (Copart uses React, so we wait for specific selector)
//            page.waitForSelector("[data-testid='lot-search-results']",
//                new Page.WaitForSelectorOptions().setTimeout(30000));
//
//            // Extract lot cards
//            List<Locator> cards = page.locator(".lot-search-results .lot-card").all();
//            log.info("Found {} Copart lot cards", cards.size());
//
//            for (Locator card : cards) {
//                try {
//                    AuctionLot lot = extractLotFromCard(card);
//                    if (lot != null) lots.add(lot);
//                } catch (Exception e) {
//                    log.warn("Failed to parse card: {}", e.getMessage());
//                }
//            }
//
//            context.browser().close();
//        } catch (Exception e) {
//            log.error("Copart parsing failed", e);
//        }
//
//        return lots;
//    }
//
//    @Override
//    public AuctionLot parseLotDetail(String lotNumber) {
//        String url = "https://www.copart.com/lot/" + lotNumber;
//
//        try (Playwright playwright = Playwright.create()) {
//            BrowserContext context = playwrightConfig.createContext(playwright);
//            Page page = context.newPage();
//            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
//
//            page.waitForSelector(".lot-details",
//                new Page.WaitForSelectorOptions().setTimeout(30000));
//
//            AuctionLot lot = new AuctionLot();
//            lot.setLotNumber(lotNumber);
//            lot.setSource(AuctionLot.AuctionSource.COPART);
//
//            // Extract VIN
//            String vin = page.locator("[data-testid='vin']").textContent().trim();
//            lot.setVin(vin);
//
//            // Extract make/model/year
//            String title = page.locator("h1.lot-title").textContent().trim();
//            parseTitle(lot, title);
//
//            // Extract price
//            String priceText = page.locator("[data-testid='current-bid']").textContent();
//            lot.setAuctionPrice(parsePrice(priceText));
//
//            // Extract damage
//            lot.setDamageType(page.locator("[data-testid='damage-type']").textContent().trim());
//            lot.setPrimaryDamage(page.locator("[data-testid='primary-damage']").textContent().trim());
//
//            // Extract odometer
//            String odometerText = page.locator("[data-testid='odometer']").textContent();
//            lot.setOdometer(parseOdometer(odometerText));
//
//            // Location
//            lot.setAuctionLocation(page.locator("[data-testid='location']").textContent().trim());
//
//            // Images
//            List<String> images = page.locator(".lot-image img").all()
//                .stream()
//                .map(img -> img.getAttribute("src"))
//                .toList();
//            lot.setImageUrls(images);
//
//            context.browser().close();
//            return lot;
//
//        } catch (Exception e) {
//            log.error("Failed to parse lot detail: {}", lotNumber, e);
//            return null;
//        }
//    }
//
//    private AuctionLot extractLotFromCard(Locator card) {
//        // Simplified extraction — adjust selectors based on actual Copart DOM
//        String lotNumber = card.locator(".lot-number").textContent().trim();
//        String title = card.locator(".lot-title").textContent().trim();
//        String priceText = card.locator(".current-bid").textContent();
//
//        AuctionLot lot = new AuctionLot();
//        lot.setLotNumber(lotNumber);
//        lot.setSource(AuctionLot.AuctionSource.COPART);
//        parseTitle(lot, title);
//        lot.setAuctionPrice(parsePrice(priceText));
//
//        return lot;
//    }
//
//    private void parseTitle(AuctionLot lot, String title) {
//        // Expecting: "2022 TOYOTA RAV4 HYBRID"
//        String[] parts = title.trim().split("\\s+", 3);
//        if (parts.length >= 2) {
//            lot.setYear(Integer.parseInt(parts[0]));
//            lot.setMake(parts[1]);
//            lot.setModel(parts.length > 2 ? parts[2] : "");
//        }
//    }
//
//    private BigDecimal parsePrice(String text) {
//        Matcher matcher = PRICE_PATTERN.matcher(text.replace(",", ""));
//        if (matcher.find()) {
//            return new BigDecimal(matcher.group(1).replace(",", ""));
//        }
//        return null;
//    }
//
//    private Integer parseOdometer(String text) {
//        try {
//            return Integer.parseInt(text.replaceAll("[^0-9]", ""));
//        } catch (NumberFormatException e) {
//            return null;
//        }
//    }
//}
