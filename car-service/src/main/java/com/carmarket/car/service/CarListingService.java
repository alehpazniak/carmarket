package com.carmarket.car.service;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.CarListing.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarListingService {

    private final CarListingRepository repository;
    private final CarListingMapper mapper;
    private final CarEventProducer eventProducer;
    private final S3Service s3Service;

    // ─── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public CarListingResponse create(CarListingRequest request, UUID sellerId) {
        CarListing car = mapper.toEntity(request);
        car.setSellerId(sellerId);
        car.setStatus(ListingStatus.ACTIVE);

        CarListing saved = repository.save(car);
        eventProducer.publishCreated(saved);

        log.info("Car listing created: {} by seller: {}", saved.getId(), sellerId);
        return mapper.toResponse(saved);
    }

    // ─── READ ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CarListingResponse getById(UUID id) {
        log.info("Getting car listing: {}", id);
        return repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new CarNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<CarListingResponse> getAll(Pageable pageable) {
        return repository.findByStatus(ListingStatus.ACTIVE, pageable)
            .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CarListingResponse> getMyListings(UUID sellerId, Pageable pageable) {
        return repository.findBySellerId(sellerId, pageable)
            .map(mapper::toResponse);
    }

    // ─── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public CarListingResponse update(UUID id, CarListingRequest request, UUID requesterId) {
        CarListing car = findAndVerifyOwner(id, requesterId);
        mapper.updateEntity(request, car);

        CarListing updated = repository.save(car);
        eventProducer.publishUpdated(updated);

        log.info("Car listing updated: {} by: {}", id, requesterId);
        return mapper.toResponse(updated);
    }

    // ─── STATUS CHANGE ─────────────────────────────────────────────────────────

    @Transactional
    public CarListingResponse markAsSold(UUID id, UUID requesterId) {
        CarListing car = findAndVerifyOwner(id, requesterId);
        car.setStatus(ListingStatus.SOLD);
        CarListing updated = repository.save(car);
        eventProducer.publishUpdated(updated);
        return mapper.toResponse(updated);
    }

    // ─── DELETE ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, UUID requesterId) {
        CarListing car = findAndVerifyOwner(id, requesterId);
        car.setStatus(ListingStatus.REMOVED);
        repository.save(car);
        eventProducer.publishDeleted(id.toString());
        log.info("Car listing removed: {} by: {}", id, requesterId);
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private CarListing findAndVerifyOwner(UUID id, UUID requesterId) {
        CarListing car = repository.findById(id)
            .orElseThrow(() -> new CarNotFoundException(id));
        if (!car.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("You don't own this listing");
        }
        return car;
    }

    // ─── IMAGES ────────────────────────────────────────────────────────────────

    @Transactional
    public List<String> uploadImages(UUID carId, UUID requesterId, List<MultipartFile> files) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        List<String> urls = s3Service.uploadImages(carId.toString(), files);
        car.getImageUrls().addAll(urls);
        repository.save(car);
        log.info("Uploaded {} images for car: {}", urls.size(), carId);
        return urls;
    }

    // ─── EXCEPTIONS ────────────────────────────────────────────────────────────

    public static class CarNotFoundException extends RuntimeException {
        public CarNotFoundException(UUID id) {
            super("Car listing not found: " + id);
        }
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String msg) {
            super(msg);
        }
    }
}
