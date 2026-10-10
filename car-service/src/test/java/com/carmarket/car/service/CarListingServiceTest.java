package com.carmarket.car.service;

import com.carmarket.car.dto.CarListingRequest;
import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.dto.TechnicalDataDto;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.TechnicalData;
import com.carmarket.car.exception.AccessDeniedException;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CarListingService Test")
@ExtendWith(MockitoExtension.class)
class CarListingServiceTest {

    @Mock
    private CarListingRepository repository;
    @Mock
    private CarListingMapper mapper;
    @Mock
    private CarEventProducer eventProducer;
    @Mock
    private S3Service s3Service;
    @Mock
    private VideoLinkService videoLinkService;

    @InjectMocks
    private CarListingService carListingService;

    private UUID carId;
    private UUID sellerId;
    private CarListing car;

    @BeforeEach
    void setUp() {
        carId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        car = CarListing.builder().id(carId).sellerId(sellerId).make("BMW").model("320i").build();
    }

    @Test
    @DisplayName("update replaces the whole technical data block when it is sent")
    void updateReplacesTechnicalData() {
        car.setTechnicalData(TechnicalData.builder().powerHp(150).vin("WBA00000000000000").build());
        TechnicalDataDto dto = TechnicalDataDto.builder().powerHp(184).build();
        TechnicalData mapped = TechnicalData.builder().powerHp(184).build();
        CarListingRequest request = CarListingRequest.builder().technicalData(dto).build();
        when(repository.findById(carId)).thenReturn(Optional.of(car));
        when(mapper.toTechnicalData(dto)).thenReturn(mapped);
        when(repository.save(car)).thenReturn(car);

        carListingService.update(carId, request, sellerId);

        assertThat(car.getTechnicalData()).isSameAs(mapped);
        verify(eventProducer).publishUpdated(car);
    }

    @Test
    @DisplayName("update keeps technical data when the request has none")
    void updateKeepsTechnicalDataWhenAbsent() {
        TechnicalData existing = TechnicalData.builder().powerHp(150).build();
        car.setTechnicalData(existing);
        when(repository.findById(carId)).thenReturn(Optional.of(car));
        when(repository.save(car)).thenReturn(car);

        carListingService.update(carId, CarListingRequest.builder().build(), sellerId);

        assertThat(car.getTechnicalData()).isSameAs(existing);
        verify(mapper, never()).toTechnicalData(any());
    }

    @Test
    @DisplayName("setVideo stores the normalized link")
    void setVideoStoresNormalizedLink() {
        when(repository.findById(carId)).thenReturn(Optional.of(car));
        when(videoLinkService.normalize("https://youtu.be/dQw4w9WgXcQ"))
            .thenReturn("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        when(repository.save(car)).thenReturn(car);
        when(mapper.toResponse(car)).thenReturn(new CarListingResponse());

        carListingService.setVideo(carId, sellerId, "https://youtu.be/dQw4w9WgXcQ");

        assertThat(car.getVideoUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("setVideo rejects a non-owner before touching the link")
    void setVideoRejectsNonOwner() {
        when(repository.findById(carId)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> carListingService.setVideo(carId, UUID.randomUUID(), "https://youtu.be/dQw4w9WgXcQ"))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(videoLinkService);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("removeVideo clears the link")
    void removeVideoClearsLink() {
        car.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        when(repository.findById(carId)).thenReturn(Optional.of(car));
        when(repository.save(car)).thenReturn(car);

        carListingService.removeVideo(carId, sellerId);

        assertThat(car.getVideoUrl()).isNull();
    }
}
