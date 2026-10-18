package com.carmarket.auction.service.shipping;

import com.carmarket.auction.config.W8ShippingConfig;
import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.entity.W8TowingRate;
import com.carmarket.auction.repository.W8TowingRateRepository;
import com.carmarket.auction.service.CurrencyExchangeService;
import com.carmarket.auction.service.shipping.W8LocationKey.ParsedLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Prices a car's way from a US auction yard to Poland from W8 Shipping's towing table and {@code import.w8}.
 * <p>
 * Matching, first hit wins (always within the configured price list):
 * <ol>
 *   <li>{@link W8MatchType#EXACT}: same auction, yard key and state (state ignored when either side has none);
 *       the input's sublot when W8 lists it, otherwise the main yard.</li>
 *   <li>{@link W8MatchType#OTHER_AUCTION}: the same yard key and state at any auction, main yard.</li>
 *   <li>{@link W8MatchType#STATE_ESTIMATE}: per port, the highest price in the state at the same auction
 *       (any auction when unknown), so the estimate never understates.</li>
 * </ol>
 * Nothing matched → empty, and callers keep their previous behaviour.
 */
@Service
@RequiredArgsConstructor
public class W8ShippingQuoteService {

    /** Cheapest to the EU border, then the shorter sea transit (unknown last), then port order. */
    static final Comparator<W8PortOption> CHEAPEST = Comparator
        .comparing(W8PortOption::toEuBorderUsd)
        .thenComparing(W8PortOption::transitDaysMax, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(W8PortOption::port);

    private final W8TowingRateRepository rates;
    private final W8ShippingConfig config;
    private final CurrencyExchangeService exchange;

    public Optional<W8ShippingQuote> quote(W8QuoteRequest req) {
        ParsedLocation parsed = W8LocationKey.parse(req.location());
        return match(req.auction(), parsed).map(m -> build(req, m));
    }

    /** Autocomplete item: one W8 location (yard or sublot) with its towing price per port. */
    public record W8Location(W8Auction auction, String location, String state, List<PortPrice> ports) {}

    public record PortPrice(W8Port port, BigDecimal towingUsd) {}

    static final int MAX_LOCATIONS = 20;

    /**
     * Locations whose yard key starts with the normalized query, all auctions when {@code auction} is null; at
     * most {@value #MAX_LOCATIONS}. A location has at most one row per port, so 6 × the limit rows always cover it.
     */
    public List<W8Location> locations(W8Auction auction, String query) {
        String prefix = W8LocationKey.normalize(query);
        if (prefix == null) return List.of();
        Map<String, List<W8TowingRate>> byLocation = new LinkedHashMap<>();
        rates.searchByYardKeyPrefix(config.getPriceList(), auction, prefix,
                PageRequest.of(0, MAX_LOCATIONS * W8Port.values().length))
            .forEach(r -> byLocation.computeIfAbsent(r.getAuction() + "|" + r.getLocation(), k -> new ArrayList<>()).add(r));
        return byLocation.values().stream()
            .limit(MAX_LOCATIONS)
            .map(rows -> new W8Location(rows.get(0).getAuction(), rows.get(0).getLocation(), rows.get(0).getState(),
                rows.stream().map(r -> new PortPrice(r.getPort(), r.getPriceUsd())).toList()))
            .toList();
    }

    private record Match(W8MatchType type, String location, W8Auction auction, List<W8TowingRate> rows) {}

    private Optional<Match> match(W8Auction auction, ParsedLocation loc) {
        String list = config.getPriceList();
        String key = loc.yardKey();
        String state = loc.state();

        if (auction != null && key != null) {
            List<W8TowingRate> yard = rates.findByPriceListAndAuctionAndYardKey(list, auction, key).stream()
                .filter(sameState(state)).toList();
            List<W8TowingRate> rows = yard.stream().filter(sublot(loc.sublot())).toList();
            if (rows.isEmpty()) rows = yard.stream().filter(r -> r.getSublot() == null).toList();
            if (!rows.isEmpty()) return Optional.of(new Match(W8MatchType.EXACT, rows.get(0).getLocation(), auction, rows));
        }

        if (key != null) {
            List<W8TowingRate> anyAuction = state != null
                ? rates.findByPriceListAndYardKeyAndState(list, key, state)
                : rates.findByPriceListAndYardKey(list, key);
            List<W8TowingRate> rows = anyAuction.stream().filter(r -> r.getSublot() == null).toList();
            if (!rows.isEmpty()) {
                // Prefer one auction's rows so the quote names a single matched yard
                W8Auction first = rows.stream().map(W8TowingRate::getAuction).min(Comparator.naturalOrder()).orElseThrow();
                List<W8TowingRate> sameAuction = rows.stream().filter(r -> r.getAuction() == first).toList();
                return Optional.of(new Match(W8MatchType.OTHER_AUCTION, sameAuction.get(0).getLocation(), first, sameAuction));
            }
        }

        if (state != null) {
            List<W8TowingRate> rows = auction != null
                ? rates.findByPriceListAndAuctionAndState(list, auction, state)
                : rates.findByPriceListAndState(list, state);
            if (!rows.isEmpty()) return Optional.of(new Match(W8MatchType.STATE_ESTIMATE, state, auction, rows));
        }
        return Optional.empty();
    }

    private static Predicate<W8TowingRate> sameState(String state) {
        return r -> state == null || r.getState() == null || state.equals(r.getState());
    }

    private static Predicate<W8TowingRate> sublot(String sublot) {
        return r -> sublot != null && r.getSublot() != null && r.getSublot().equalsIgnoreCase(sublot);
    }

    private W8ShippingQuote build(W8QuoteRequest req, Match match) {
        ContainerShare share = ContainerShare.of(req.suv());

        // Highest price per port: one row per port for a yard, several for a state estimate
        Map<W8Port, BigDecimal> towing = new EnumMap<>(W8Port.class);
        match.rows().forEach(r -> towing.merge(r.getPort(), r.getPriceUsd(), BigDecimal::max));

        List<W8PortOption> options = towing.entrySet().stream()
            .map(e -> option(e.getKey(), e.getValue(), share))
            .sorted(CHEAPEST)
            .toList();
        W8PortOption chosen = options.get(0);

        W8ShippingConfig.Rotterdam rotterdam = config.getRotterdam();
        BigDecimal forwarderEur = rotterdam.getForwarderEur().get(share);
        PolandZone zone = req.polandZone() != null ? req.polandZone() : config.getPolandDeliveryPln().getDefaultZone();
        BigDecimal polandDeliveryPln = config.getPolandDeliveryPln().getZones().get(zone)
            .add(config.getPolandDeliveryPln().surcharge(req.lengthClass()));
        boolean fees = config.isIncludeW8Fees();
        BigDecimal accessFeeUsd = fees ? config.getAccessFeeUsd() : BigDecimal.ZERO;
        BigDecimal dealerFeeUsd = fees ? config.getDealerFeeUsd() : BigDecimal.ZERO;

        BigDecimal usdPln = exchange.getUsdToPlnRate();
        BigDecimal eurPln = exchange.getEurToPlnRate();
        BigDecimal afterBorderPln = forwarderEur.add(rotterdam.getBrokerEur()).add(rotterdam.getScanEur())
            .multiply(eurPln).add(polandDeliveryPln).setScale(2, RoundingMode.HALF_UP);

        return new W8ShippingQuote(config.getPriceList(), match.type(), match.location(), match.auction(), share,
            options, chosen, forwarderEur, rotterdam.getBrokerEur(), rotterdam.getScanEur(), zone, polandDeliveryPln,
            accessFeeUsd, dealerFeeUsd, chosen.toEuBorderUsd(), afterBorderPln, accessFeeUsd.add(dealerFeeUsd),
            usdPln, eurPln);
    }

    private W8PortOption option(W8Port port, BigDecimal towingUsd, ContainerShare share) {
        W8ShippingConfig.Port info = config.getPorts().getOrDefault(port, new W8ShippingConfig.Port(port.name(), null, null));
        Freight freight = oceanFreight(port, share);
        return new W8PortOption(port, info.getLabel(), towingUsd, freight.usd(), freight.source(),
            info.getTransitDaysMin(), info.getTransitDaysMax(), towingUsd.add(freight.usd()));
    }

    record Freight(BigDecimal usd, OceanFreightSource source) {}

    /**
     * The configured price for the share; else derived from the other share by the container split
     * (third = quarter × 4/3, quarter = third × 3/4, whole dollars); else the fallback, marked as an estimate.
     */
    Freight oceanFreight(W8Port port, ContainerShare share) {
        W8ShippingConfig.ShareRates configured = config.getOceanFreightUsd().get(port);
        if (configured != null) {
            if (configured.get(share) != null) return new Freight(configured.get(share), OceanFreightSource.W8_PRICE_LIST);
            if (share == ContainerShare.THIRD && configured.getQuarter() != null) {
                return new Freight(scale(configured.getQuarter(), 4, 3), OceanFreightSource.DERIVED);
            }
            if (share == ContainerShare.QUARTER && configured.getThird() != null) {
                return new Freight(scale(configured.getThird(), 3, 4), OceanFreightSource.DERIVED);
            }
        }
        return new Freight(config.getOceanFreightFallbackUsd(), OceanFreightSource.ESTIMATE);
    }

    private static BigDecimal scale(BigDecimal value, int multiply, int divide) {
        return value.multiply(BigDecimal.valueOf(multiply)).divide(BigDecimal.valueOf(divide), 0, RoundingMode.HALF_UP);
    }
}
