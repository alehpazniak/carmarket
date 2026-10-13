package com.carmarket.car.service;

import com.carmarket.car.audit.AdminAuditPublisher;
import com.carmarket.car.audit.AdminAuditPublisher.Actor;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.ReportReason;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.kafka.ListingModeratedProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminListingServiceTest {

    private final CarListingRepository listings = mock(CarListingRepository.class);
    private final ListingReportRepository reports = mock(ListingReportRepository.class);
    private final CarEventProducer carEvents = mock(CarEventProducer.class);
    private final ListingModeratedProducer moderated = mock(ListingModeratedProducer.class);
    private final AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
    private final S3Service s3 = mock(S3Service.class);
    private final AdminListingService service = new AdminListingService(listings, reports, mock(CarListingMapper.class),
        carEvents, moderated, audit, s3, mock(NamedParameterJdbcTemplate.class));
    private final Actor admin = new Actor(UUID.randomUUID().toString(), "boss@example.com");
    private CarListing car;

    @BeforeEach
    void setUp() {
        car = new CarListing();
        car.setId(UUID.randomUUID());
        car.setSellerId(UUID.randomUUID());
        car.setMake("Audi");
        car.setModel("A4");
        car.setYear(2019);
        car.setStatus(ListingStatus.ACTIVE);
        car.setImageUrls(new ArrayList<>(List.of("https://s3/a.jpg", "https://s3/b.jpg")));
        car.setPrimaryImageUrl("https://s3/a.jpg");
        when(listings.findByIdForUpdate(car.getId())).thenReturn(Optional.of(car));
        when(listings.findAnyById(car.getId())).thenReturn(Optional.of(car));
        when(listings.save(any(CarListing.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void blockingHidesTheListingResolvesReportsAndTellsTheSeller() {
        ListingReport open = ListingReport.builder().id(UUID.randomUUID()).carId(car.getId()).reporterId(UUID.randomUUID())
            .reason(ReportReason.SCAM).status(ReportStatus.OPEN).build();
        when(reports.findByCarIdAndStatus(car.getId(), ReportStatus.OPEN)).thenReturn(List.of(open));

        service.block(car.getId(), "fake listing", admin);

        assertThat(car.getStatus()).isEqualTo(ListingStatus.BLOCKED);
        assertThat(car.getModerationReason()).isEqualTo("fake listing");
        assertThat(car.getModeratedBy()).hasToString(admin.id());
        assertThat(open.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(open.getResolutionNote()).isEqualTo("fake listing");
        verify(carEvents).publishUpdated(car);
        verify(moderated).publish(argThat(e -> e.action().equals("BLOCKED") && e.reason().equals("fake listing")
            && e.sellerId().equals(car.getSellerId().toString()) && e.title().equals("Audi A4 (2019)")));
        verify(audit).publish(eq(admin), eq("LISTING_BLOCKED"), eq("LISTING"), eq(car.getId().toString()), eq("fake listing"), any());
    }

    @Test
    void blockingTwiceDoesNothingTheSecondTime() {
        car.setStatus(ListingStatus.BLOCKED);
        service.block(car.getId(), "fake listing", admin);
        verify(moderated, never()).publish(any());
        verify(audit, never()).publish(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void unblockRestoresActiveAndClearsTheReason() {
        car.setStatus(ListingStatus.BLOCKED);
        car.setModerationReason("fake listing");

        service.unblock(car.getId(), "seller fixed it", admin);

        assertThat(car.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(car.getModerationReason()).isNull();
        verify(moderated).publish(argThat(e -> e.action().equals("UNBLOCKED")));
        verify(carEvents).publishUpdated(car);
    }

    @Test
    void onlyBlockedListingsCanBeUnblocked() {
        car.setStatus(ListingStatus.SUSPENDED);
        assertThatThrownBy(() -> service.unblock(car.getId(), "seller fixed it", admin))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(car.getStatus()).isEqualTo(ListingStatus.SUSPENDED);
    }

    @Test
    void removingThePrimaryPhotoPromotesTheNextOneAndDeletesTheObject() {
        service.removeImage(car.getId(), "https://s3/a.jpg", "offensive photo", admin);

        assertThat(car.getImageUrls()).containsExactly("https://s3/b.jpg");
        assertThat(car.getPrimaryImageUrl()).isEqualTo("https://s3/b.jpg");
        verify(s3).deleteImage("https://s3/a.jpg");
        verify(audit).publish(eq(admin), eq("LISTING_IMAGE_REMOVED"), eq("LISTING"), eq(car.getId().toString()),
            eq("offensive photo"), any());
    }

    @Test
    void aForeignPhotoUrlIsRejected() {
        assertThatThrownBy(() -> service.removeImage(car.getId(), "https://evil/x.jpg", "offensive photo", admin))
            .isInstanceOf(ResponseStatusException.class);
        verify(s3, never()).deleteImage(anyString());
    }

    @Test
    void dismissingClosesOnlyOpenReports() {
        ListingReport open = ListingReport.builder().id(UUID.randomUUID()).status(ReportStatus.OPEN).reason(ReportReason.OTHER).build();
        when(reports.findByCarIdAndStatus(car.getId(), ReportStatus.OPEN)).thenReturn(List.of(open));

        assertThat(service.dismissReports(car.getId(), "looks fine", admin)).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        verify(audit).publish(eq(admin), eq("REPORTS_DISMISSED"), eq("LISTING"), eq(car.getId().toString()), eq("looks fine"), any());
    }
}
