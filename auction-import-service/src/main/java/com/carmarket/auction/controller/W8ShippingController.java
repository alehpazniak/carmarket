package com.carmarket.auction.controller;

import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.service.shipping.LengthClass;
import com.carmarket.auction.service.shipping.PolandZone;
import com.carmarket.auction.service.shipping.W8QuoteRequest;
import com.carmarket.auction.service.shipping.W8ShippingQuote;
import com.carmarket.auction.service.shipping.W8ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * W8 Shipping transport calculator ("Kalkulator transportu W8" on /aukcje). Logged-in users only: the default
 * rule in SecurityConfig, and the gateway requires a JWT for every /api/auctions path that isn't listed public.
 */
@RestController
@RequestMapping("/api/auctions/shipping/w8")
@RequiredArgsConstructor
public class W8ShippingController {

    private final W8ShippingQuoteService quoteService;

    /** Autocomplete: up to 20 W8 locations whose name starts with {@code q} (at least 2 characters). */
    @GetMapping("/locations")
    public List<W8ShippingQuoteService.W8Location> locations(@RequestParam(required = false) String auction,
                                                             @RequestParam(required = false) String q) {
        if (q == null || q.trim().length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q needs at least 2 characters");
        }
        return quoteService.locations(auction(auction), q);
    }

    /** 404 when W8's table has nothing for the location, not even in its state. */
    @GetMapping("/quote")
    public ResponseEntity<W8ShippingQuote> quote(@RequestParam(required = false) String auction,
                                                 @RequestParam String location,
                                                 @RequestParam(required = false) Boolean suv,
                                                 @RequestParam(required = false) PolandZone zone,
                                                 @RequestParam(required = false) LengthClass length) {
        if (location.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "location is too long");
        }
        return quoteService.quote(new W8QuoteRequest(auction(auction), location, suv, zone, length))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Blank = any auction; an unknown name is a 400, not silently "any". */
    private static W8Auction auction(String value) {
        if (value == null || value.isBlank()) return null;
        return W8Auction.from(value).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown auction: " + value));
    }
}
