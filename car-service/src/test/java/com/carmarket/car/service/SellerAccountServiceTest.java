package com.carmarket.car.service;

import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.repository.CarListingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerAccountServiceTest {

    private final CarListingRepository repository = mock(CarListingRepository.class);
    private final CarEventProducer producer = mock(CarEventProducer.class);
    private final SellerAccountService service = new SellerAccountService(repository, producer);
    private final UUID seller = UUID.randomUUID();

    private CarListing listing(ListingStatus status) {
        CarListing car = new CarListing();
        car.setId(UUID.randomUUID());
        car.setSellerId(seller);
        car.setStatus(status);
        return car;
    }

    @Test
    void blockingSuspendsOnlyActiveListingsAndRepublishesThem() {
        CarListing active = listing(ListingStatus.ACTIVE);
        when(repository.findBySellerIdAndStatus(seller, ListingStatus.ACTIVE)).thenReturn(List.of(active));
        when(repository.save(any(CarListing.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.suspendListings(seller)).isEqualTo(1);

        assertThat(active.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
        verify(producer).publishUpdated(active);
    }

    @Test
    void unblockingRestoresOnlySuspendedListings() {
        CarListing suspended = listing(ListingStatus.SUSPENDED);
        when(repository.findBySellerIdAndStatus(seller, ListingStatus.SUSPENDED)).thenReturn(List.of(suspended));
        when(repository.save(any(CarListing.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restoreListings(seller);

        assertThat(suspended.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        verify(repository).findBySellerIdAndStatus(seller, ListingStatus.SUSPENDED);
        verify(repository, never()).findBySellerIdAndStatus(seller, ListingStatus.SOLD);
        verify(repository, never()).findBySellerIdAndStatus(seller, ListingStatus.DRAFT);
    }

    @Test
    void suspendedAndRemovedListingsAreHiddenPublicly() {
        assertThat(ListingStatus.SUSPENDED.isPubliclyVisible()).isFalse();
        assertThat(ListingStatus.REMOVED.isPubliclyVisible()).isFalse();
        assertThat(ListingStatus.ACTIVE.isPubliclyVisible()).isTrue();
        assertThat(ListingStatus.SOLD.isPubliclyVisible()).isTrue();
    }
}
