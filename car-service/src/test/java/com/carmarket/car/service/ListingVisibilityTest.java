package com.carmarket.car.service;

import com.carmarket.car.dto.CarListingResponse;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.exception.CarNotFoundException;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/** BLOCKED / SUSPENDED listings are hidden publicly but visible to their owner via /cars/my/{id}. */
@ExtendWith(MockitoExtension.class)
class ListingVisibilityTest {

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
    private CarListingService service;

    private CarListing car;

    @BeforeEach
    void setUp() {
        car = new CarListing();
        car.setId(UUID.randomUUID());
        car.setSellerId(UUID.randomUUID());
        lenient().when(repository.findById(car.getId())).thenReturn(Optional.of(car));
        lenient().when(mapper.toResponse(car)).thenReturn(CarListingResponse.builder().id(car.getId()).build());
    }

    @Test
    void blockedAndSuspendedListingsAre404PubliclyButVisibleToTheOwner() {
        for (ListingStatus status : new ListingStatus[]{ListingStatus.BLOCKED, ListingStatus.SUSPENDED}) {
            car.setStatus(status);
            assertThatThrownBy(() -> service.getById(car.getId())).as(status.name()).isInstanceOf(CarNotFoundException.class);
            assertThat(service.getMine(car.getId(), car.getSellerId())).isNotNull();
        }
    }

    @Test
    void someoneElsesListingIsNotFoundUnderMy() {
        car.setStatus(ListingStatus.BLOCKED);
        assertThatThrownBy(() -> service.getMine(car.getId(), UUID.randomUUID())).isInstanceOf(CarNotFoundException.class);
    }

    @Test
    void activeAndSoldStayPublic() {
        car.setStatus(ListingStatus.SOLD);
        assertThat(service.getById(car.getId())).isNotNull();
    }

    @Test
    void aBlockedListingCantEscapeByBeingMarkedSold() {
        car.setStatus(ListingStatus.BLOCKED);
        assertThatThrownBy(() -> service.markAsSold(car.getId(), car.getSellerId())).isInstanceOf(ResponseStatusException.class);
        assertThat(car.getStatus()).isEqualTo(ListingStatus.BLOCKED);
    }
}
