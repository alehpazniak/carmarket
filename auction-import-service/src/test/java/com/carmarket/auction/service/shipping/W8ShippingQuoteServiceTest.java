package com.carmarket.auction.service.shipping;

import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.entity.W8TowingRate;
import com.carmarket.auction.repository.W8TowingRateRepository;
import com.carmarket.auction.service.CurrencyExchangeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class W8ShippingQuoteServiceTest {

    static final String LIST = "W8-2025-26";

    private final W8TowingRateRepository repo = mock(W8TowingRateRepository.class);
    private final W8ShippingConfig config = new W8ShippingConfig();
    private final W8ShippingQuoteService service = new W8ShippingQuoteService(repo, config, exchange());

    static CurrencyExchangeService exchange() {
        CurrencyExchangeService exchange = mock(CurrencyExchangeService.class);
        when(exchange.getUsdToPlnRate()).thenReturn(new BigDecimal("4.00"));
        when(exchange.getEurToPlnRate()).thenReturn(new BigDecimal("4.30"));
        when(exchange.getEurToUsdRate()).thenReturn(new BigDecimal("1.0750"));
        return exchange;
    }

    static W8TowingRate rate(W8Auction auction, String yard, String state, String sublot, W8Port port, int price) {
        String location = yard + (state != null ? " - " + state : "") + (sublot != null ? " (" + sublot + ")" : "");
        return W8TowingRate.builder().priceList(LIST).auction(auction).location(location).yard(yard).state(state)
            .sublot(sublot).yardKey(W8LocationKey.normalize(yard)).port(port).priceUsd(BigDecimal.valueOf(price)).build();
    }

    private W8ShippingQuote quote(W8Auction auction, String location) {
        return service.quote(new W8QuoteRequest(auction, location, false, null, null)).orElseThrow();
    }

    /** W8's own worked example: Copart Somerville NJ, sedan → towing $175 + ocean $525 to the EU border. */
    @Test
    void w8ExampleSomervilleSedan() {
        when(repo.findByPriceListAndAuctionAndYardKey(LIST, W8Auction.COPART, "SOMERVILLE")).thenReturn(List.of(
            rate(W8Auction.COPART, "SOMERVILLE", "NJ", null, W8Port.NEW_YORK, 175),
            rate(W8Auction.COPART, "SOMERVILLE", "NJ", "Offsite South Amboy", W8Port.NEW_YORK, 175)));

        W8ShippingQuote q = quote(W8Auction.COPART, "NJ - SOMERVILLE");

        assertThat(q.matchType()).isEqualTo(W8MatchType.EXACT);
        assertThat(q.matchedLocation()).isEqualTo("SOMERVILLE - NJ");
        assertThat(q.containerShare()).isEqualTo(ContainerShare.QUARTER);
        assertThat(q.chosen().port()).isEqualTo(W8Port.NEW_YORK);
        assertThat(q.chosen().towingUsd()).isEqualByComparingTo("175");
        assertThat(q.chosen().oceanFreightUsd()).isEqualByComparingTo("525");
        assertThat(q.chosen().oceanFreightSource()).isEqualTo(OceanFreightSource.W8_PRICE_LIST);
        assertThat(q.toEuBorderUsd()).isEqualByComparingTo("700");
        assertThat(q.brokerEur()).isEqualByComparingTo("100");
        assertThat(q.forwarderEur()).isEqualByComparingTo("430");
        assertThat(q.accessFeeUsd()).isEqualByComparingTo("75");
        assertThat(q.dealerFeeUsd()).isEqualByComparingTo("125");
        assertThat(q.w8FeesUsd()).isEqualByComparingTo("200");
        // (430 + 100 + 50) € × 4.30 + 2,300 zł (zone A)
        assertThat(q.afterBorderPln()).isEqualByComparingTo("4794.00");
    }

    @Test
    void exactUsesTheSublotWhenW8ListsItElseTheMainYard() {
        when(repo.findByPriceListAndAuctionAndYardKey(LIST, W8Auction.COPART, "CHICAGO SOUTH")).thenReturn(List.of(
            rate(W8Auction.COPART, "CHICAGO SOUTH", "IL", null, W8Port.NEW_YORK, 400),
            rate(W8Auction.COPART, "CHICAGO SOUTH", "IL", "Heights Sublot", W8Port.NEW_YORK, 450)));

        assertThat(quote(W8Auction.COPART, "CHICAGO SOUTH - IL (heights sublot)").chosen().towingUsd()).isEqualByComparingTo("450");
        assertThat(quote(W8Auction.COPART, "CHICAGO SOUTH - IL (Other Sublot)").chosen().towingUsd()).isEqualByComparingTo("400");
        assertThat(quote(W8Auction.COPART, "Chicago South (IL)").chosen().towingUsd()).isEqualByComparingTo("400");
        // A different state is not the same yard
        assertThat(service.quote(new W8QuoteRequest(W8Auction.COPART, "Chicago South (IN)", false, null, null))).isEmpty();
    }

    @Test
    void otherAuctionAtTheSameLocation() {
        when(repo.findByPriceListAndYardKeyAndState(LIST, "BILLINGS", "MT")).thenReturn(List.of(
            rate(W8Auction.COPART, "BILLINGS", "MT", null, W8Port.SEATTLE, 850),
            rate(W8Auction.COPART, "BILLINGS", "MT", null, W8Port.HOUSTON, 1250)));

        W8ShippingQuote q = quote(W8Auction.MANHEIM, "Billings (MT)");

        assertThat(q.matchType()).isEqualTo(W8MatchType.OTHER_AUCTION);
        assertThat(q.auction()).isEqualTo(W8Auction.COPART);
        // Seattle $850 + $1,200 beats Houston $1,250 + $1,200; both are listed
        assertThat(q.chosen().port()).isEqualTo(W8Port.SEATTLE);
        assertThat(q.options()).extracting(W8PortOption::port).containsExactly(W8Port.SEATTLE, W8Port.HOUSTON);
    }

    @Test
    void stateEstimateTakesTheHighestPricePerPort() {
        when(repo.findByPriceListAndAuctionAndState(LIST, W8Auction.IAAI, "MT")).thenReturn(List.of(
            rate(W8Auction.IAAI, "BILLINGS", "MT", null, W8Port.SEATTLE, 850),
            rate(W8Auction.IAAI, "MISSOULA", "MT", null, W8Port.SEATTLE, 900)));

        W8ShippingQuote q = quote(W8Auction.IAAI, "Bozeman (MT)");

        assertThat(q.matchType()).isEqualTo(W8MatchType.STATE_ESTIMATE);
        assertThat(q.matchedLocation()).isEqualTo("MT");
        assertThat(q.chosen().towingUsd()).isEqualByComparingTo("900");
    }

    @Test
    void stateEstimateWithoutAnAuctionUsesEveryAuction() {
        when(repo.findByPriceListAndState(LIST, "MT")).thenReturn(List.of(
            rate(W8Auction.COPART, "BILLINGS", "MT", null, W8Port.SEATTLE, 850)));

        assertThat(quote(null, "MT").matchType()).isEqualTo(W8MatchType.STATE_ESTIMATE);
    }

    @Test
    void noMatchIsEmpty() {
        assertThat(service.quote(new W8QuoteRequest(W8Auction.COPART, "Honolulu (HI)", false, null, null))).isEmpty();
        assertThat(service.quote(new W8QuoteRequest(W8Auction.COPART, null, false, null, null))).isEmpty();
    }

    @Test
    void equalCostsPickTheShorterTransitThenThePortOrder() {
        // Savannah 30–35 days vs Norfolk (no published transit) at the same price
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            rate(W8Auction.IAAI, "GREENSBORO", "NC", null, W8Port.NORFOLK, 325),
            rate(W8Auction.IAAI, "GREENSBORO", "NC", null, W8Port.SAVANNAH, 325)));
        assertThat(quote(W8Auction.IAAI, "GREENSBORO - NC").chosen().port()).isEqualTo(W8Port.SAVANNAH);

        // Los Angeles 90 days vs Houston 35 days
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            rate(W8Auction.COPART, "PHOENIX", "AZ", null, W8Port.LOS_ANGELES, 400),
            rate(W8Auction.COPART, "PHOENIX", "AZ", null, W8Port.HOUSTON, 400)));
        assertThat(quote(W8Auction.COPART, "PHOENIX - AZ").chosen().port()).isEqualTo(W8Port.HOUSTON);

        // Same price and transit: enum order (Savannah before Houston)
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            rate(W8Auction.COPART, "MOBILE", "AL", null, W8Port.HOUSTON, 400),
            rate(W8Auction.COPART, "MOBILE", "AL", null, W8Port.SAVANNAH, 400)));
        assertThat(quote(W8Auction.COPART, "MOBILE - AL").chosen().port()).isEqualTo(W8Port.SAVANNAH);
    }

    @Test
    void oceanFreightSources() {
        assertThat(service.oceanFreight(W8Port.NEW_YORK, ContainerShare.QUARTER))
            .isEqualTo(new W8ShippingQuoteService.Freight(new BigDecimal("525"), OceanFreightSource.W8_PRICE_LIST));
        // 525 × 4/3
        assertThat(service.oceanFreight(W8Port.NEW_YORK, ContainerShare.THIRD))
            .isEqualTo(new W8ShippingQuoteService.Freight(new BigDecimal("700"), OceanFreightSource.DERIVED));
        assertThat(service.oceanFreight(W8Port.SAVANNAH, ContainerShare.QUARTER))
            .isEqualTo(new W8ShippingQuoteService.Freight(new BigDecimal("1200"), OceanFreightSource.ESTIMATE));

        config.getOceanFreightUsd().put(W8Port.HOUSTON, new W8ShippingConfig.ShareRates(null, new BigDecimal("801")));
        // 801 × 3/4 = 600.75 → 601
        assertThat(service.oceanFreight(W8Port.HOUSTON, ContainerShare.QUARTER))
            .isEqualTo(new W8ShippingQuoteService.Freight(new BigDecimal("601"), OceanFreightSource.DERIVED));
    }

    @Test
    void suvZoneAndLength() {
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            rate(W8Auction.COPART, "SOMERVILLE", "NJ", null, W8Port.NEW_YORK, 175)));

        W8ShippingQuote q = service.quote(new W8QuoteRequest(W8Auction.COPART, "SOMERVILLE - NJ", true,
            PolandZone.C, LengthClass.OVER_5_3M)).orElseThrow();

        assertThat(q.containerShare()).isEqualTo(ContainerShare.THIRD);
        assertThat(q.forwarderEur()).isEqualByComparingTo("480");
        assertThat(q.chosen().oceanFreightSource()).isEqualTo(OceanFreightSource.DERIVED);
        assertThat(q.polandZone()).isEqualTo(PolandZone.C);
        assertThat(q.polandDeliveryPln()).isEqualByComparingTo("2500"); // 2,100 + 400
        assertThat(service.quote(new W8QuoteRequest(W8Auction.COPART, "SOMERVILLE - NJ", false, PolandZone.B,
            LengthClass.OVER_5M)).orElseThrow().polandDeliveryPln()).isEqualByComparingTo("2400"); // 2,200 + 200
    }

    @Test
    void w8FeesCanBeTurnedOff() {
        when(repo.findByPriceListAndAuctionAndYardKey(any(), any(), any())).thenReturn(List.of(
            rate(W8Auction.COPART, "SOMERVILLE", "NJ", null, W8Port.NEW_YORK, 175)));
        config.setIncludeW8Fees(false);

        W8ShippingQuote q = quote(W8Auction.COPART, "SOMERVILLE - NJ");

        assertThat(q.accessFeeUsd()).isEqualByComparingTo("0");
        assertThat(q.dealerFeeUsd()).isEqualByComparingTo("0");
        assertThat(q.w8FeesUsd()).isEqualByComparingTo("0");
    }
}
