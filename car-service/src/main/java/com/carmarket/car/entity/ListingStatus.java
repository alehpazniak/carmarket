package com.carmarket.car.entity;

public enum ListingStatus {

    ACTIVE, SOLD, DRAFT, REMOVED,
    /** The owner's account is blocked; hidden publicly, back to ACTIVE when the account is unblocked. */
    SUSPENDED;

    /** Whether the public GET /cars/{id} shows the listing (the owner still sees it via /cars/my). */
    public boolean isPubliclyVisible() {
        return this != SUSPENDED && this != REMOVED;
    }
}
