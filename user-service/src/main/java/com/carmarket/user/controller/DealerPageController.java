package com.carmarket.user.controller;

import com.carmarket.user.dto.DealerPageDtos.DealerCard;
import com.carmarket.user.dto.DealerPageDtos.DealerPageOwnerView;
import com.carmarket.user.dto.DealerPageDtos.DealerPageRequest;
import com.carmarket.user.dto.DealerPageDtos.DealerPageView;
import com.carmarket.user.dto.PhoneNumberResponse;
import com.carmarket.user.service.DealerPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public dealer pages (/dealer/{slug} in the frontend).
 * <p>
 * GET /users/dealers/{slug}        — the page (public, allow-listed fields, no phone)
 * GET /users/dealers/by-user/{id} — name, logo and slug of a seller's page, for the box on their listings (public)
 * GET /users/dealers/{slug}/phone  — the business phone for "Wyświetl numer" (public, rate-limited per IP at the gateway)
 * GET /users/me/dealer-page        — the caller's own page, 404 if none yet (DEALER/ADMIN)
 * PUT /users/me/dealer-page        — create or replace the caller's page (DEALER/ADMIN)
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class DealerPageController {

    private final DealerPageService service;

    @GetMapping("/dealers/{slug}")
    public ResponseEntity<DealerPageView> getPage(@PathVariable String slug) {
        return service.findPublic(slug)
            .map(v -> ResponseEntity.ok(DealerPageView.from(v.page(), v.profile().getCreatedAt())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/dealers/by-user/{userId}")
    public ResponseEntity<DealerCard> getCard(@PathVariable UUID userId) {
        return service.findPublicByUserId(userId)
            .map(v -> ResponseEntity.ok(DealerCard.from(v.page())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/dealers/{slug}/phone")
    public ResponseEntity<PhoneNumberResponse> getPhone(@PathVariable String slug) {
        // Never log the number itself
        log.info("Dealer page phone requested: slug={}", slug);
        return service.findPublicPhone(slug)
            .map(phone -> ResponseEntity.ok(new PhoneNumberResponse(phone)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/me/dealer-page")
    public ResponseEntity<DealerPageOwnerView> getOwn(@RequestHeader("X-User-Id") String userId) {
        return service.findOwn(UUID.fromString(userId))
            .map(page -> ResponseEntity.ok(DealerPageOwnerView.from(page)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/me/dealer-page")
    public ResponseEntity<DealerPageOwnerView> save(@RequestHeader("X-User-Id") String userId,
                                                    @Valid @RequestBody DealerPageRequest request) {
        return ResponseEntity.ok(DealerPageOwnerView.from(service.save(UUID.fromString(userId), request)));
    }
}
