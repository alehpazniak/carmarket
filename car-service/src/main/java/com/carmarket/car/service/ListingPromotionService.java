package com.carmarket.car.service;

import com.carmarket.car.dto.PaymentEvent;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingPromotionPayment;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingPromotionPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Applies paid promotions from payment-service (payment.completed / payment.refunded) to listings
 * and expires them.
 * <ul>
 *   <li>Purchases stack: {@code until = max(now, current) + durationDays}.</li>
 *   <li>Every processed payment is recorded in listing_promotion_payments, so a redelivered event
 *       is a no-op and a refund subtracts exactly the days that were added.</li>
 *   <li>A payment for a missing, inactive or someone else's listing is recorded with 0 days and
 *       not applied; refunding it is a manual admin action in payment-service.</li>
 * </ul>
 * Every change re-publishes car.updated so search-service re-ranks the listing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingPromotionService {

    static final int DEFAULT_DURATION_DAYS = 7;

    private final CarListingRepository carListingRepository;
    private final ListingPromotionPaymentRepository promotionPaymentRepository;
    private final CarEventProducer eventProducer;
    private final Clock clock;

    /** The listing field each sellable product extends. */
    enum Product {
        LISTING_PROMOTION(CarListing::getPromotedUntil, CarListing::setPromotedUntil),
        LISTING_HIGHLIGHT(CarListing::getHighlightedUntil, CarListing::setHighlightedUntil);

        final Function<CarListing, LocalDateTime> until;
        final BiConsumer<CarListing, LocalDateTime> setUntil;

        Product(Function<CarListing, LocalDateTime> until, BiConsumer<CarListing, LocalDateTime> setUntil) {
            this.until = until;
            this.setUntil = setUntil;
        }

        static Optional<Product> of(String code) {
            return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
        }
    }

    @Transactional
    public void applyCompleted(PaymentEvent event) {
        Optional<Product> product = Product.of(event.productCode());
        if (product.isEmpty()) {
            log.debug("Ignoring payment {} for product {}", event.paymentId(), event.productCode());
            return;
        }
        UUID paymentId = parseUuid(event.paymentId());
        UUID carId = parseUuid(event.referenceId());
        if (paymentId == null || carId == null) {
            log.warn("Ignoring payment.completed with invalid paymentId '{}' / referenceId '{}'",
                event.paymentId(), event.referenceId());
            return;
        }
        if (promotionPaymentRepository.existsById(paymentId)) {
            log.info("Payment {} already processed, skipping redelivery", paymentId);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<CarListing> found = carListingRepository.findByIdForUpdate(carId); // REMOVED is hidden by @SQLRestriction
        String rejection = found.isEmpty() ? "listing not found"
            : found.get().getStatus() != ListingStatus.ACTIVE ? "listing is " + found.get().getStatus()
            : !found.get().getSellerId().toString().equals(event.userId()) ? "buyer " + event.userId() + " is not the seller"
            : null;
        if (rejection != null) {
            log.warn("Payment {} ({}) not applied to listing {}: {} — refund it manually if needed",
                paymentId, event.productCode(), carId, rejection);
            record(paymentId, carId, event.productCode(), 0, now);
            return;
        }

        CarListing car = found.get();
        int days = event.durationDays() != null && event.durationDays() > 0
            ? event.durationDays() : DEFAULT_DURATION_DAYS;
        LocalDateTime current = product.get().until.apply(car);
        LocalDateTime base = current != null && current.isAfter(now) ? current : now;
        product.get().setUntil.accept(car, base.plusDays(days));

        CarListing saved = carListingRepository.save(car);
        record(paymentId, carId, event.productCode(), days, now);
        eventProducer.publishUpdated(saved);
        log.info("Payment {}: {} on listing {} extended by {} days until {}",
            paymentId, event.productCode(), carId, days, product.get().until.apply(saved));
    }

    @Transactional
    public void applyRefunded(PaymentEvent event) {
        UUID paymentId = parseUuid(event.paymentId());
        Optional<ListingPromotionPayment> recorded = paymentId == null
            ? Optional.empty() : promotionPaymentRepository.findById(paymentId);
        if (recorded.isEmpty()) {
            log.debug("Ignoring refund of payment {}: never applied here", event.paymentId());
            return;
        }
        ListingPromotionPayment payment = recorded.get();
        if (payment.getRefundedAt() != null) {
            log.info("Refund of payment {} already processed, skipping redelivery", paymentId);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        payment.setRefundedAt(now);
        promotionPaymentRepository.save(payment);

        Optional<Product> product = Product.of(payment.getProductCode());
        if (payment.getDaysApplied() == 0 || product.isEmpty()) {
            return;
        }
        Optional<CarListing> found = carListingRepository.findByIdForUpdate(payment.getCarId());
        if (found.isEmpty()) {
            log.info("Refund of payment {}: listing {} no longer exists", paymentId, payment.getCarId());
            return;
        }
        CarListing car = found.get();
        LocalDateTime current = product.get().until.apply(car);
        if (current == null) {
            return; // already expired and cleared
        }
        LocalDateTime reduced = current.minusDays(payment.getDaysApplied());
        product.get().setUntil.accept(car, reduced.isAfter(now) ? reduced : null);

        CarListing saved = carListingRepository.save(car);
        eventProducer.publishUpdated(saved);
        log.info("Refund of payment {}: removed {} days of {} from listing {}",
            paymentId, payment.getDaysApplied(), payment.getProductCode(), payment.getCarId());
    }

    /** Clears promotions that have run out. @return number of listings changed */
    @Transactional
    public int expirePromotions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UUID> ids = carListingRepository.findIdsWithExpiredPromotion(now);
        int changed = 0;
        for (UUID id : ids) {
            // Re-read under lock: a purchase may have extended it since the query
            Optional<CarListing> found = carListingRepository.findByIdForUpdate(id);
            if (found.isEmpty()) {
                continue;
            }
            CarListing car = found.get();
            boolean dirty = false;
            for (Product product : Product.values()) {
                LocalDateTime until = product.until.apply(car);
                if (until != null && !until.isAfter(now)) {
                    product.setUntil.accept(car, null);
                    dirty = true;
                }
            }
            if (dirty) {
                eventProducer.publishUpdated(carListingRepository.save(car));
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Expired promotions on {} listings", changed);
        }
        return changed;
    }

    private void record(UUID paymentId, UUID carId, String productCode, int days, LocalDateTime now) {
        promotionPaymentRepository.save(ListingPromotionPayment.builder()
            .paymentId(paymentId)
            .carId(carId)
            .productCode(productCode)
            .daysApplied(days)
            .appliedAt(now)
            .build());
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
