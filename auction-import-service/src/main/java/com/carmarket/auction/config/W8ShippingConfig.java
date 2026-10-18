package com.carmarket.auction.config;

import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.service.shipping.ContainerShare;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * W8 Shipping's prices outside the towing table ({@code import.w8} in application.yml, which documents the
 * source of every number). The Java defaults are the same values, so a plain {@code new W8ShippingConfig()}
 * behaves like the shipped configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "import.w8")
public class W8ShippingConfig {

    /** Must match {@code w8_towing_rate.price_list}. */
    private String priceList = "W8-2025-26";
    /** Auction access + dealer fee, charged when buying through W8. */
    private boolean includeW8Fees = true;
    private BigDecimal accessFeeUsd = new BigDecimal("75");
    private BigDecimal dealerFeeUsd = new BigDecimal("125");
    /** Per car to Rotterdam, by container share; ports W8 hasn't priced use {@link #oceanFreightFallbackUsd}. */
    private Map<W8Port, ShareRates> oceanFreightUsd = new EnumMap<>(Map.of(W8Port.NEW_YORK, new ShareRates(new BigDecimal("525"), null)));
    private BigDecimal oceanFreightFallbackUsd = new BigDecimal("1200");
    private Map<W8Port, Port> ports = defaultPorts();
    private Rotterdam rotterdam = new Rotterdam();
    private PolandDelivery polandDeliveryPln = new PolandDelivery();
    private BigDecimal eurPlnFallback = new BigDecimal("4.30");

    /** A price per container share; either may be missing. */
    @Data
    public static class ShareRates {
        private BigDecimal quarter;
        private BigDecimal third;

        public ShareRates() {
        }

        public ShareRates(BigDecimal quarter, BigDecimal third) {
            this.quarter = quarter;
            this.third = third;
        }

        public BigDecimal get(ContainerShare share) {
            return share == ContainerShare.THIRD ? third : quarter;
        }
    }

    @Data
    public static class Port {
        private String label;
        /** Sea transit to Rotterdam; null where W8 hasn't published it. */
        private Integer transitDaysMin;
        private Integer transitDaysMax;

        public Port() {
        }

        public Port(String label, Integer transitDaysMin, Integer transitDaysMax) {
            this.label = label;
            this.transitDaysMin = transitDaysMin;
            this.transitDaysMax = transitDaysMax;
        }
    }

    @Data
    public static class Rotterdam {
        private ShareRates forwarderEur = new ShareRates(new BigDecimal("430"), new BigDecimal("480"));
        private BigDecimal brokerEur = new BigDecimal("100");
        private BigDecimal scanEur = new BigDecimal("50");
    }

    @Data
    public static class PolandDelivery {
        private Map<PolandZone, BigDecimal> zones = new EnumMap<>(Map.of(
            PolandZone.A, new BigDecimal("2300"), PolandZone.B, new BigDecimal("2200"), PolandZone.C, new BigDecimal("2100")));
        private PolandZone defaultZone = PolandZone.A;
        private BigDecimal over5mSurcharge = new BigDecimal("200");
        private BigDecimal over53mSurcharge = new BigDecimal("400");

        public BigDecimal surcharge(LengthClass length) {
            return switch (length == null ? LengthClass.STANDARD : length) {
                case STANDARD -> BigDecimal.ZERO;
                case OVER_5M -> over5mSurcharge;
                case OVER_5_3M -> over53mSurcharge;
            };
        }
    }

    private static Map<W8Port, Port> defaultPorts() {
        Map<W8Port, Port> ports = new EnumMap<>(W8Port.class);
        ports.put(W8Port.SAVANNAH, new Port("Savannah (Garden City, GA)", 30, 35));
        ports.put(W8Port.LOS_ANGELES, new Port("Los Angeles (Carson, CA)", 90, 90));
        ports.put(W8Port.NEW_YORK, new Port("New York (Linden, NJ)", 25, 30));
        ports.put(W8Port.HOUSTON, new Port("Houston (Baytown, TX)", 30, 35));
        ports.put(W8Port.SEATTLE, new Port("Seattle (Tacoma, WA)", null, null));
        ports.put(W8Port.NORFOLK, new Port("Norfolk (Chesapeake, VA)", null, null));
        return ports;
    }
}
