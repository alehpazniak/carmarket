package com.carmarket.notification.entity;

/** Add a constant here (and a case in EmailContentFactory) for every new kind of email. */
public enum NotificationType {
    NEW_CHAT_MESSAGE,
    DEALER_UPGRADE_CONFIRMATION,
    /** A client of a dealer's import order: something new on their /zlecenie page (never the password). */
    IMPORT_ORDER_UPDATE,
    /** listing.moderated BLOCKED: a moderator hid the seller's listing, with the reason. */
    LISTING_BLOCKED,
    /** listing.moderated UNBLOCKED: the listing is visible again. */
    LISTING_RESTORED
}
