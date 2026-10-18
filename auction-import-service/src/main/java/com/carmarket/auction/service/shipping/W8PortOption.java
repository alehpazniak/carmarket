package com.carmarket.auction.service.shipping;

import com.carmarket.auction.entity.W8Port;

import java.math.BigDecimal;

/** One way to the EU border: towing to a W8 port plus ocean freight from there to Rotterdam. */
public record W8PortOption(
    W8Port port,
    String portLabel,
    BigDecimal towingUsd,
    BigDecimal oceanFreightUsd,
    OceanFreightSource oceanFreightSource,
    Integer transitDaysMin,
    Integer transitDaysMax,
    BigDecimal toEuBorderUsd
) {
}
