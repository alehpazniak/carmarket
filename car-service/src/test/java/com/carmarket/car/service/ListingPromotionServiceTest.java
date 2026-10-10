package com.carmarket.car.service;

import com.carmarket.car.dto.PaymentEvent;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingPromotionPayment;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingPromotionPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ListingPromotionService")
class ListingPromotionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 12, 0);

    private final CarListingRepository carRepository = mock(CarListingRepository.class);
    private final ListingPromotionPaymentRepository paymentRepository = mock(ListingPromotionPaymentRepository.class);
    private final CarEventProducer eventProducer = mock(CarEventProducer.class);
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private final ListingPromotionService service =
        new ListingPromotionService(carRepository, paymentRepository, eventProducer, clock);

    /** In-memory listing_promotion_payments, so redelivery behaves like with a real table. */
    private final Map<UUID, ListingPromotionPayment> recorded = new HashMap<>();

    private UUID sellerId;
    private CarListing car;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        car = CarListing.builder()
            .id(UUID.randomUUID())
            .sellerId(sellerId)
            .make("Toyota").model("Corolla").year(2020)
            .price(new BigDecimal("50000"))
            .status(ListingStatus.ACTIVE)
            .build();
        when(carRepository.findByIdForUpdate(car.getId())).thenReturn(Optional.of(car));
        when(carRepository.save(any(CarListing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.existsById(any())).thenAnswer(inv -> recorded.containsKey(inv.<UUID>getArgument(0)));
        when(paymentRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(recorded.get(inv.<UUID>getArgument(0))));
        when(paymentRepository.save(any(ListingPromotionPayment.class))).thenAnswer(inv -> {
            ListingPromotionPayment p = inv.getArgument(0);
            recorded.put(p.getPaymentId(), p);
            return p;
        });
    }

    @Nested
    @DisplayName("payment.completed")
    class Completed {

        @Test
        void promotesListingForDurationDays() {
            PaymentEvent event = event("LISTING_PROMOTION", 7);

            service.applyCompleted(event);

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
            assertThat(car.getHighlightedUntil()).isNull();
            assertThat(recorded.get(UUID.fromString(event.paymentId())).getDaysApplied()).isEqualTo(7);
            verify(eventProducer).publishUpdated(car);
        }

        @Test
        void highlightProductSetsHighlightedUntil() {
            service.applyCompleted(event("LISTING_HIGHLIGHT", 7));

            assertThat(car.getHighlightedUntil()).isEqualTo(NOW.plusDays(7));
            assertThat(car.getPromotedUntil()).isNull();
        }

        @Test
        void purchasesStackOnActivePromotion() {
            car.setPromotedUntil(NOW.plusDays(3));

            service.applyCompleted(event("LISTING_PROMOTION", 7));
            service.applyCompleted(event("LISTING_PROMOTION", 7));

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(17));
        }

        @Test
        void expiredPromotionRestartsFromNow() {
            car.setPromotedUntil(NOW.minusDays(2));

            service.applyCompleted(event("LISTING_PROMOTION", 7));

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
        }

        @Test
        void missingDurationDefaultsToSevenDays() {
            service.applyCompleted(event("LISTING_PROMOTION", null));

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
        }

        @Test
        void redeliveryIsIgnored() {
            PaymentEvent event = event("LISTING_PROMOTION", 7);

            service.applyCompleted(event);
            service.applyCompleted(event);

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
            verify(eventProducer).publishUpdated(car); // once
        }

        @Test
        void wrongOwnerIsRecordedButNotApplied() {
            PaymentEvent event = event("LISTING_PROMOTION", 7, UUID.randomUUID().toString());

            service.applyCompleted(event);

            assertThat(car.getPromotedUntil()).isNull();
            assertThat(recorded.get(UUID.fromString(event.paymentId())).getDaysApplied()).isZero();
            verify(carRepository, never()).save(any());
            verify(eventProducer, never()).publishUpdated(any());
        }

        @Test
        void inactiveListingIsRecordedButNotApplied() {
            car.setStatus(ListingStatus.SOLD);
            PaymentEvent event = event("LISTING_PROMOTION", 7);

            service.applyCompleted(event);

            assertThat(car.getPromotedUntil()).isNull();
            assertThat(recorded.get(UUID.fromString(event.paymentId())).getDaysApplied()).isZero();
            verify(eventProducer, never()).publishUpdated(any());
        }

        @Test
        void missingListingIsRecordedButNotApplied() {
            when(carRepository.findByIdForUpdate(car.getId())).thenReturn(Optional.empty());
            PaymentEvent event = event("LISTING_PROMOTION", 7);

            service.applyCompleted(event);

            assertThat(recorded.get(UUID.fromString(event.paymentId())).getDaysApplied()).isZero();
            verify(eventProducer, never()).publishUpdated(any());
        }

        @Test
        void otherProductsAreIgnored() {
            service.applyCompleted(event("DEALER_SUBSCRIPTION", 30));

            assertThat(recorded).isEmpty();
            verify(carRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void invalidReferenceIdIsIgnored() {
            PaymentEvent event = new PaymentEvent(UUID.randomUUID().toString(), sellerId.toString(),
                "LISTING_PROMOTION", "not-a-uuid", 1999, "PLN", 1L, Instant.now(), 7);

            service.applyCompleted(event);

            assertThat(recorded).isEmpty();
            verify(carRepository, never()).findByIdForUpdate(any());
        }
    }

    @Nested
    @DisplayName("payment.refunded")
    class Refunded {

        @Test
        void subtractsAppliedDaysAndKeepsOtherPurchases() {
            PaymentEvent first = event("LISTING_PROMOTION", 7);
            PaymentEvent second = event("LISTING_PROMOTION", 7);
            service.applyCompleted(first);
            service.applyCompleted(second);

            service.applyRefunded(second);

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
            assertThat(recorded.get(UUID.fromString(second.paymentId())).getRefundedAt()).isEqualTo(NOW);
        }

        @Test
        void clearsPromotionWhenNothingIsLeft() {
            PaymentEvent event = event("LISTING_PROMOTION", 7);
            service.applyCompleted(event);

            service.applyRefunded(event);

            assertThat(car.getPromotedUntil()).isNull();
            verify(eventProducer, times(2)).publishUpdated(car); // applied, then refunded
        }

        @Test
        void refundRedeliveryIsIgnored() {
            PaymentEvent first = event("LISTING_PROMOTION", 7);
            PaymentEvent second = event("LISTING_PROMOTION", 7);
            service.applyCompleted(first);
            service.applyCompleted(second);

            service.applyRefunded(second);
            service.applyRefunded(second);

            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
        }

        @Test
        void refundOfRejectedPaymentChangesNothing() {
            car.setStatus(ListingStatus.SOLD);
            PaymentEvent event = event("LISTING_PROMOTION", 7);
            service.applyCompleted(event);

            service.applyRefunded(event);

            assertThat(recorded.get(UUID.fromString(event.paymentId())).getRefundedAt()).isEqualTo(NOW);
            verify(carRepository, never()).save(any());
            verify(eventProducer, never()).publishUpdated(any());
        }

        @Test
        void refundOfUnknownPaymentIsIgnored() {
            service.applyRefunded(event("LISTING_PROMOTION", 7));

            assertThat(recorded).isEmpty();
            verify(eventProducer, never()).publishUpdated(any());
        }
    }

    @Nested
    @DisplayName("expiry")
    class Expiry {

        @Test
        void clearsExpiredFieldsAndPublishes() {
            car.setPromotedUntil(NOW.minusMinutes(1));
            car.setHighlightedUntil(NOW.plusDays(2));
            when(carRepository.findIdsWithExpiredPromotion(NOW)).thenReturn(List.of(car.getId()));

            int changed = service.expirePromotions();

            assertThat(changed).isEqualTo(1);
            assertThat(car.getPromotedUntil()).isNull();
            assertThat(car.getHighlightedUntil()).isEqualTo(NOW.plusDays(2));
            verify(eventProducer).publishUpdated(car);
        }

        @Test
        void skipsListingExtendedSinceTheQuery() {
            car.setPromotedUntil(NOW.plusDays(7)); // re-read under lock shows a fresh purchase
            when(carRepository.findIdsWithExpiredPromotion(NOW)).thenReturn(List.of(car.getId()));

            int changed = service.expirePromotions();

            assertThat(changed).isZero();
            assertThat(car.getPromotedUntil()).isEqualTo(NOW.plusDays(7));
            verify(eventProducer, never()).publishUpdated(any());
        }
    }

    private PaymentEvent event(String productCode, Integer days) {
        return event(productCode, days, sellerId.toString());
    }

    private PaymentEvent event(String productCode, Integer days, String userId) {
        return new PaymentEvent(UUID.randomUUID().toString(), userId, productCode, car.getId().toString(),
            1999, "PLN", 1L, Instant.now(), days);
    }
}
