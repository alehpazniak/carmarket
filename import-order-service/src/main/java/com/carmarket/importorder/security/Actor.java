package com.carmarket.importorder.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The signed-in dealer (or admin) behind a request, from the gateway-signed X-User-Id / X-User-Roles.
 * Admins may read every order; changes are always limited to the caller's own orders.
 */
public record Actor(UUID userId, boolean admin) {

    public static Actor from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new Actor(UUID.fromString(authentication.getName()), admin);
    }
}
