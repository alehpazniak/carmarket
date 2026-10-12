package com.carmarket.car.service;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.dto.CarListingSummary;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.exception.AccessDeniedException;
import com.carmarket.car.exception.CarNotFoundException;
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarListingService {

    private final CarListingRepository repository;
    private final CarListingMapper mapper;
    private final CarEventProducer eventProducer;
    private final S3Service s3Service;
    private final VideoLinkService videoLinkService;

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
            .filter(car -> car.getStatus().isPubliclyVisible())
            .map(mapper::toResponse)
            .orElseThrow(() -> new CarNotFoundException(id.toString()));
    }

    /** Most ids one summaries request may ask for. */
    public static final int MAX_SUMMARY_IDS = 100;

    /**
     * Summaries for the given ids, REMOVED listings included; unknown ids are left out.
     *
     * @throws IllegalArgumentException if more than {@value #MAX_SUMMARY_IDS} distinct ids are asked for
     */
    @Transactional(readOnly = true)
    public List<CarListingSummary> getSummaries(Collection<UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        if (distinct.size() > MAX_SUMMARY_IDS) {
            throw new IllegalArgumentException("At most " + MAX_SUMMARY_IDS + " ids per request");
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        return repository.findSummariesByIdIn(distinct).stream()
            .map(v -> new CarListingSummary(v.getId(), v.getMake(), v.getModel(), v.getYear(), v.getPrice(),
                v.getPrimaryImageUrl(), ListingStatus.valueOf(v.getStatus())))
            .toList();
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
        if (request.getTechnicalData() != null) {
            car.setTechnicalData(mapper.toTechnicalData(request.getTechnicalData()));
        }

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
            .orElseThrow(() -> new CarNotFoundException(id.toString()));
        if (!car.getSellerId().equals(requesterId)) {
            throw new AccessDeniedException("You don't own this listing");
        }
        return car;
    }

    // ─── IMAGES ────────────────────────────────────────────────────────────────

    @Transactional
    public List<String> uploadImages(UUID carId, UUID requesterId, List<MultipartFile> files) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        List<String> urls = s3Service.uploadImages(carId, files);
        car.getImageUrls().addAll(urls);
        if (car.getPrimaryImageUrl() == null && !urls.isEmpty()) {
            car.setPrimaryImageUrl(urls.get(0));
        }
        CarListing updated = repository.save(car);
        eventProducer.publishUpdated(updated);
        log.info("Uploaded {} images for car: {}", urls.size(), carId);
        return urls;
    }

    // ─── IMAGE MANAGEMENT ──────────────────────────────────────────────────────

    @Transactional
    public CarListingResponse setPrimaryImage(UUID carId, UUID requesterId, String url) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        if (!car.getImageUrls().contains(url)) {
            throw new IllegalArgumentException("Image does not belong to this listing");
        }
        car.setPrimaryImageUrl(url);
        CarListing updated = repository.save(car);
        eventProducer.publishUpdated(updated);
        log.info("Set primary image for car: {}", carId);
        return mapper.toResponse(updated);
    }

    @Transactional
    public CarListingResponse removeImage(UUID carId, UUID requesterId, String url) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        if (!car.getImageUrls().remove(url)) {
            throw new IllegalArgumentException("Image does not belong to this listing");
        }
        if (url.equals(car.getPrimaryImageUrl())) {
            car.setPrimaryImageUrl(car.getImageUrls().stream().findFirst().orElse(null));
        }
        CarListing updated = repository.save(car);
        s3Service.deleteImage(url);
        eventProducer.publishUpdated(updated);
        log.info("Removed image for car: {}", carId);
        return mapper.toResponse(updated);
    }

    // ─── VIDEO ─────────────────────────────────────────────────────────────────

    @Transactional
    public CarListingResponse setVideo(UUID carId, UUID requesterId, String url) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        car.setVideoUrl(videoLinkService.normalize(url));
        CarListing updated = repository.save(car);
        log.info("Set video for car: {}", carId);
        return mapper.toResponse(updated);
    }

    @Transactional
    public CarListingResponse removeVideo(UUID carId, UUID requesterId) {
        CarListing car = findAndVerifyOwner(carId, requesterId);
        car.setVideoUrl(null);
        CarListing updated = repository.save(car);
        log.info("Removed video for car: {}", carId);
        return mapper.toResponse(updated);
    }
}
