package com.carmarket.auction.service.shipping;

import com.carmarket.auction.entity.W8Auction;

import java.math.BigDecimal;
import java.util.List;

/**
 * W8 Shipping's cost of getting one car from a US auction yard to Poland; also the JSON shape (camelCase).
 * <p>
 * Inside the customs value (transport up to the EU border): only {@code chosen.towingUsd} and
 * {@code chosen.oceanFreightUsd}, i.e. {@link #toEuBorderUsd}. Outside it: the Rotterdam forwarder, customs
 * broker and scan, the truck to Poland ({@link #afterBorderPln}) and W8's own fees ({@link #w8FeesUsd}).
 *
 * @param matchedLocation W8's display name of the matched yard, or the state for {@link W8MatchType#STATE_ESTIMATE}
 * @param auction         the auction whose row matched; the requested one (maybe null) for a state estimate
 * @param options         every port with a towing price, cheapest first; {@code chosen} is the first
 * @param polandDeliveryPln zone price + length surcharge
 * @param dealerFeeUsd    0 (as is {@code accessFeeUsd}) when {@code import.w8.include-w8-fees} is false
 * @param afterBorderPln  (forwarder + broker + scan) × eurPlnRate + polandDeliveryPln
 * @param w8FeesUsd       accessFeeUsd + dealerFeeUsd
 */
public record W8ShippingQuote(
    String priceList,
    W8MatchType matchType,
    String matchedLocation,
    W8Auction auction,
    ContainerShare containerShare,
    List<W8PortOption> options,
    W8PortOption chosen,
    BigDecimal forwarderEur,
    BigDecimal brokerEur,
    BigDecimal scanEur,
    PolandZone polandZone,
    BigDecimal polandDeliveryPln,
    BigDecimal accessFeeUsd,
    BigDecimal dealerFeeUsd,
    BigDecimal toEuBorderUsd,
    BigDecimal afterBorderPln,
    BigDecimal w8FeesUsd,
    BigDecimal usdPlnRate,
    BigDecimal eurPlnRate
) {
}
