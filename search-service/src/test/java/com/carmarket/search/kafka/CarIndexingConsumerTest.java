package com.carmarket.search.kafka;

import com.carmarket.search.document.CarDocument;
import com.carmarket.search.dto.CarUpdatedEvent;
import com.carmarket.search.service.CarSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("CarIndexingConsumer promotion fields")
class CarIndexingConsumerTest {

    private final CarIndexingConsumer consumer = new CarIndexingConsumer(mock(CarSearchService.class));

    @Test
    void futurePromotionAndHighlightAreActive() {
        LocalDateTime promotedUntil = LocalDateTime.now().plusDays(7);
        LocalDateTime highlightedUntil = LocalDateTime.now().plusDays(3);

        CarDocument doc = consumer.toDocument(event(promotedUntil, highlightedUntil));

        assertThat(doc.isPromoted()).isTrue();
        assertThat(doc.isHighlighted()).isTrue();
        assertThat(doc.getPromotedUntil()).isEqualTo(promotedUntil.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(doc.getHighlightedUntil()).isEqualTo(highlightedUntil.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void expiredPromotionIsNotActive() {
        CarDocument doc = consumer.toDocument(event(LocalDateTime.now().minusMinutes(1), null));

        assertThat(doc.isPromoted()).isFalse();
        assertThat(doc.isHighlighted()).isFalse();
        assertThat(doc.getPromotedUntil()).isNotNull();
        assertThat(doc.getHighlightedUntil()).isNull();
    }

    @Test
    void neverPromotedListing() {
        CarDocument doc = consumer.toDocument(event(null, null));

        assertThat(doc.isPromoted()).isFalse();
        assertThat(doc.getPromotedUntil()).isNull();
    }

    private static CarUpdatedEvent event(LocalDateTime promotedUntil, LocalDateTime highlightedUntil) {
        return new CarUpdatedEvent("car-1", "seller-1", "Toyota", "Corolla", 2020, new BigDecimal("50000"),
            100000, "PETROL", "MANUAL", "Warszawa", "ACTIVE", null, LocalDateTime.now().minusDays(1),
            "PASSENGER", promotedUntil, highlightedUntil);
    }
}
