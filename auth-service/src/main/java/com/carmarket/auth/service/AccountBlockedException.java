package com.carmarket.auth.service;

/** Login of a blocked account (users.is_active = false). */
public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(String userId) {
        super("Account " + userId + " is blocked");
    }
}
