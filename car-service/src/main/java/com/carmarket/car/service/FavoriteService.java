package com.carmarket.car.service;

import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.entity.Favorite;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CarListingRepository carListingRepository;
    private final CarListingMapper mapper;

    /** Add a car to the user's favorites. Idempotent: adding twice is a no-op (returns false). */
    @Transactional
    public boolean addFavorite(UUID userId, UUID carId) {
        if (!carListingRepository.existsById(carId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found: " + carId);
        }
        if (favoriteRepository.existsByUserIdAndCarId(userId, carId)) {
            return false; // already favorited
        }
        favoriteRepository.save(Favorite.builder()
            .userId(userId)
            .carId(carId)
            .build());
        log.debug("User {} favorited car {}", userId, carId);
        return true;
    }

    /** Remove a car from favorites. Throws 404 if it wasn't favorited. */
    @Transactional
    public void removeFavorite(UUID userId, UUID carId) {
        long removed = favoriteRepository.deleteByUserIdAndCarId(userId, carId);
        if (removed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found");
        }
        log.debug("User {} unfavorited car {}", userId, carId);
    }

    @Transactional(readOnly = true)
    public Page<CarListingResponse> getFavorites(UUID userId, Pageable pageable) {
        return favoriteRepository.findFavoriteCars(userId, pageable)
            .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID carId) {
        return favoriteRepository.existsByUserIdAndCarId(userId, carId);
    }
}
