package com.carmarket.importorder.entity;

import java.util.Set;

public enum DocumentCategory {
    INVOICE_DEALER,
    INVOICE_AUCTION,
    TITLE,
    BILL_OF_LADING,
    CUSTOMS,
    TRANSPORT,
    PAYMENT_CONFIRMATION,
    CONTRACT,
    OTHER;

    /** What a client may file their own uploads under; everything else is the dealer's. */
    public static final Set<String> CLIENT_UPLOADABLE = Set.of(PAYMENT_CONFIRMATION.name(), OTHER.name());
}
