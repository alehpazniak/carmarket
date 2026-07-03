package com.carmarket.car.controller;

import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Favorites API. Identity comes from X-User-Id (validated via gateway signature).
 * <p>
 * POST   /cars/{id}/favorite   → add to favorites
 * DELETE /cars/{id}/favorite   → remove from favorites
 * GET    /cars/{id}/favorite   → is this car favorited by me?
 * GET    /cars/favorites       → my favorited listings (paged)
 */
@RestController
@RequestMapping("/cars")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping("/favorites")
    public ResponseEntity<Page<CarListingResponse>> myFavorites(@RequestHeader("X-User-Id") String userId,
                                                                @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(favoriteService.getFavorites(UUID.fromString(userId), pageable));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> add(@PathVariable UUID id, @RequestHeader("X-User-Id") String userId) {
        boolean added = favoriteService.addFavorite(UUID.fromString(userId), id);
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> remove(@PathVariable UUID id, @RequestHeader("X-User-Id") String userId) {
        favoriteService.removeFavorite(UUID.fromString(userId), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/favorite")
    public ResponseEntity<Map<String, Boolean>> isFavorite(@PathVariable UUID id,
                                                           @RequestHeader("X-User-Id") String userId) {
        boolean fav = favoriteService.isFavorite(UUID.fromString(userId), id);
        return ResponseEntity.ok(Map.of("favorite", fav));
    }
}
