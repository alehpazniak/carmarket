package com.carmarket.auction.service.shipping;

/** Share of a 40ft container: 4 sedans (1/4) or 3 SUVs (1/3) per container. */
public enum ContainerShare {
    QUARTER, THIRD;

    public static ContainerShare of(Boolean suv) {
        return Boolean.TRUE.equals(suv) ? THIRD : QUARTER;
    }
}
