package com.carmarket.car.kafka;

import com.carmarket.car.dto.CarUpdatedEvent;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.CarListing.FuelType;
import com.carmarket.car.entity.CarListing.ListingStatus;
import com.carmarket.car.entity.CarListing.Transmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CarEventProducer using JUnit5 and Mockito.
 * Tests Kafka event publishing for car listing lifecycle events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CarEventProducer Tests")
class CarEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CarEventProducer carEventProducer;

    private CarListing testCar;
    private UUID testCarId;
    private UUID testSellerId;
    private LocalDateTime testCreatedAt;

    @BeforeEach
    void setUp() {
        testCarId = UUID.randomUUID();
        testSellerId = UUID.randomUUID();
        testCreatedAt = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        testCar = CarListing.builder()
            .id(testCarId)
            .sellerId(testSellerId)
            .make("Toyota")
            .model("Corolla")
            .year(2020)
            .price(new BigDecimal("25000.00"))
            .mileage(45000)
            .fuelType(FuelType.PETROL)
            .transmission(Transmission.AUTOMATIC)
            .color("Silver")
            .city("Warsaw")
            .country("Poland")
            .description("Well-maintained sedan")
            .status(ListingStatus.ACTIVE)
            .createdAt(testCreatedAt)
            .imageUrls(new ArrayList<>())
            .build();
    }

    // ==================== PUBLISH CREATED TESTS ====================

    @Test
    @DisplayName("Should publish car.created event with typed CarUpdatedEvent")
    void testPublishCreated_Success() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("car.created");
        assertThat(keyCaptor.getValue()).isEqualTo(testCarId.toString());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event)
            .isNotNull()
            .extracting("carId", "sellerId", "make", "model", "year", "city", "status")
            .containsExactly(
                testCarId.toString(),
                testSellerId.toString(),
                "Toyota",
                "Corolla",
                2020,
                "Warsaw",
                "ACTIVE"
            );
    }

    @Test
    @DisplayName("Should map all car fields to CarUpdatedEvent")
    void testPublishCreated_AllFields() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("car.created"), anyString(), eventCaptor.capture());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event)
            .hasFieldOrPropertyWithValue("carId", testCarId.toString())
            .hasFieldOrPropertyWithValue("sellerId", testSellerId.toString())
            .hasFieldOrPropertyWithValue("make", "Toyota")
            .hasFieldOrPropertyWithValue("model", "Corolla")
            .hasFieldOrPropertyWithValue("year", 2020)
            .hasFieldOrPropertyWithValue("price", new BigDecimal("25000.00"))
            .hasFieldOrPropertyWithValue("mileage", 45000)
            .hasFieldOrPropertyWithValue("fuelType", "PETROL")
            .hasFieldOrPropertyWithValue("transmission", "AUTOMATIC")
            .hasFieldOrPropertyWithValue("city", "Warsaw")
            .hasFieldOrPropertyWithValue("status", "ACTIVE")
            .hasFieldOrPropertyWithValue("createdAt", testCreatedAt);
    }

    @Test
    @DisplayName("Should handle null optional fields in created event")
    void testPublishCreated_NullOptionalFields() {
        // Arrange
        testCar.setFuelType(null);
        testCar.setTransmission(null);
        testCar.setMileage(null);

        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("car.created"), anyString(), eventCaptor.capture());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event.fuelType()).isNull();
        assertThat(event.transmission()).isNull();
        assertThat(event.mileage()).isNull();
    }

    @Test
    @DisplayName("Should convert enum fields to string names")
    void testPublishCreated_EnumConversion() {
        // Arrange
        testCar.setFuelType(FuelType.DIESEL);
        testCar.setTransmission(Transmission.MANUAL);
        testCar.setStatus(ListingStatus.SOLD);

        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event.fuelType()).isEqualTo("DIESEL");
        assertThat(event.transmission()).isEqualTo("MANUAL");
        assertThat(event.status()).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("Should use car ID as Kafka message key")
    void testPublishCreated_MessageKey() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), any());

        assertThat(keyCaptor.getValue()).isEqualTo(testCarId.toString());
    }

    // ==================== PUBLISH UPDATED TESTS ====================

    @Test
    @DisplayName("Should publish car.updated event successfully")
    void testPublishUpdated_Success() {
        // Arrange
        testCar.setPrice(new BigDecimal("24000.00"));
        testCar.setMileage(50000);

        // Act
        carEventProducer.publishUpdated(testCar);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), eq(testCarId.toString()), eventCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("car.updated");

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event.price()).isEqualTo(new BigDecimal("24000.00"));
        assertThat(event.mileage()).isEqualTo(50000);
    }

    @Test
    @DisplayName("Should preserve all car data when publishing updated")
    void testPublishUpdated_DataPreservation() {
        // Act
        carEventProducer.publishUpdated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("car.updated"), anyString(), eventCaptor.capture());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event.make()).isEqualTo(testCar.getMake());
        assertThat(event.model()).isEqualTo(testCar.getModel());
        assertThat(event.year()).isEqualTo(testCar.getYear());
        assertThat(event.price()).isEqualTo(testCar.getPrice());
    }

    // ==================== PUBLISH DELETED TESTS ====================

    @Test
    @DisplayName("Should publish car.deleted event successfully")
    void testPublishDeleted_Success() {
        // Act
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("car.deleted");
        assertThat(keyCaptor.getValue()).isEqualTo(testCarId.toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventCaptor.getValue();
        assertThat(event).containsEntry("carId", testCarId.toString());
    }

    @Test
    @DisplayName("Should minimize payload for deleted event")
    void testPublishDeleted_MinimalPayload() {
        // Act
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("car.deleted"), anyString(), eventCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventCaptor.getValue();
        assertThat(event).hasSize(1);  // Only carId
    }

    // ==================== EVENT TYPE TESTS ====================

    @Test
    @DisplayName("Should create CarUpdatedEvent instance for created")
    void testEventType_Created() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(CarUpdatedEvent.class);
    }

    @Test
    @DisplayName("Should create CarUpdatedEvent instance for updated")
    void testEventType_Updated() {
        // Act
        carEventProducer.publishUpdated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(CarUpdatedEvent.class);
    }

    @Test
    @DisplayName("Should include createdAt timestamp in event")
    void testEventType_CreatedAtField() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());

        CarUpdatedEvent event = (CarUpdatedEvent) eventCaptor.getValue();
        assertThat(event.createdAt()).isEqualTo(testCreatedAt);
    }

    // ==================== KAFKA TEMPLATE INTERACTION TESTS ====================

    @Test
    @DisplayName("Should call KafkaTemplate.send once per created event")
    void testKafkaInteraction_Created_CallCount() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should call KafkaTemplate.send once per updated event")
    void testKafkaInteraction_Updated_CallCount() {
        // Act
        carEventProducer.publishUpdated(testCar);

        // Assert
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should call KafkaTemplate.send once per deleted event")
    void testKafkaInteraction_Deleted_CallCount() {
        // Act
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not interact with KafkaTemplate when method not called")
    void testKafkaInteraction_NoCallWithoutPublish() {
        // Act - do nothing

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should handle multiple sequential events")
    void testKafkaInteraction_MultipleEvents() {
        // Act
        carEventProducer.publishCreated(testCar);
        carEventProducer.publishUpdated(testCar);
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any());
    }

    // ==================== TOPIC ROUTING TESTS ====================

    @Test
    @DisplayName("Should use car.created topic")
    void testTopicRouting_Created() {
        // Act
        carEventProducer.publishCreated(testCar);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

        assertThat(topicCaptor.getValue()).isEqualTo("car.created");
    }

    @Test
    @DisplayName("Should use car.updated topic")
    void testTopicRouting_Updated() {
        // Act
        carEventProducer.publishUpdated(testCar);

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

        assertThat(topicCaptor.getValue()).isEqualTo("car.updated");
    }

    @Test
    @DisplayName("Should use car.deleted topic")
    void testTopicRouting_Deleted() {
        // Act
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

        assertThat(topicCaptor.getValue()).isEqualTo("car.deleted");
    }

    @Test
    @DisplayName("Should distinguish between event types by topic")
    void testTopicRouting_Distinct() {
        // Act
        carEventProducer.publishCreated(testCar);
        carEventProducer.publishUpdated(testCar);
        carEventProducer.publishDeleted(testCarId.toString());

        // Assert
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(3)).send(topicCaptor.capture(), anyString(), any());

        assertThat(topicCaptor.getAllValues())
            .contains("car.created", "car.updated", "car.deleted");
    }
}