package com.carmarket.car.service;

import com.carmarket.car.audit.AdminAuditPublisher;
import com.carmarket.car.dto.AdminListingDtos.AdminListingRow;
import com.carmarket.car.dto.ReportDtos.ReportGroup;
import com.carmarket.car.entity.CarListing;
import com.carmarket.car.entity.ListingReport;
import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.entity.ReportReason;
import com.carmarket.car.entity.ReportStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.kafka.ListingModeratedProducer;
import com.carmarket.car.mapper.CarListingMapperImpl;
import com.carmarket.car.repository.CarListingRepository;
import com.carmarket.car.repository.ListingReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The native admin queries against a real Postgres (Flyway + ddl-auto, as in the service). */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=update", "spring.flyway.table=flyway_schema_history_car",
    "spring.flyway.baseline-on-migrate=true", "spring.flyway.baseline-version=6"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdminListingService.class, CarListingMapperImpl.class})
@Testcontainers
class AdminListingQueriesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private CarEventProducer carEvents;
    @MockBean
    private ListingModeratedProducer moderated;
    @MockBean
    private AdminAuditPublisher audit;
    @MockBean
    private S3Service s3;

    @Autowired
    private AdminListingService service;
    @Autowired
    private CarListingRepository listings;
    @Autowired
    private ListingReportRepository reports;

    private CarListing save(String make, ListingStatus status, UUID seller) {
        return listings.saveAndFlush(CarListing.builder().sellerId(seller).make(make).model("X").year(2020)
            .price(BigDecimal.TEN).status(status).build());
    }

    @Test
    void listIncludesRemovedListingsAndFilters() {
        UUID seller = UUID.randomUUID();
        save("Audi", ListingStatus.ACTIVE, seller);
        save("Bmw", ListingStatus.REMOVED, seller);
        save("Opel", ListingStatus.ACTIVE, UUID.randomUUID());

        Page<AdminListingRow> bySeller = service.search(new AdminListingService.ListingFilter(null, seller, null, null, null, null), 0, 20, null);
        assertThat(bySeller.getTotalElements()).isEqualTo(2);
        assertThat(bySeller.getContent()).extracting(AdminListingRow::status).containsExactlyInAnyOrder("ACTIVE", "REMOVED");

        Page<AdminListingRow> removed = service.search(new AdminListingService.ListingFilter("REMOVED", null, null, null, null, null), 0, 20, null);
        assertThat(removed.getContent()).extracting(AdminListingRow::make).containsExactly("Bmw");

        Page<AdminListingRow> text = service.search(new AdminListingService.ListingFilter(null, null, "ope", null, null, null), 0, 20, null);
        assertThat(text.getContent()).extracting(AdminListingRow::make).containsExactly("Opel");
    }

    @Test
    void reportQueueGroupsByListingAndStatsCount() {
        CarListing car = save("Audi", ListingStatus.ACTIVE, UUID.randomUUID());
        reports.saveAndFlush(ListingReport.builder().carId(car.getId()).reporterId(UUID.randomUUID()).reason(ReportReason.SCAM).build());
        reports.saveAndFlush(ListingReport.builder().carId(car.getId()).reporterId(UUID.randomUUID()).reason(ReportReason.DUPLICATE).build());

        Page<ReportGroup> queue = service.reportQueue(ReportStatus.OPEN, 0, 20);
        assertThat(queue.getContent()).hasSize(1);
        assertThat(queue.getContent().get(0).count()).isEqualTo(2);
        assertThat(queue.getContent().get(0).reasons()).containsExactly("DUPLICATE", "SCAM");
        assertThat(queue.getContent().get(0).title()).isEqualTo("Audi X (2020)");

        var stats = service.stats();
        assertThat(stats.openReports()).isEqualTo(2);
        assertThat(stats.listingsWithOpenReports()).isEqualTo(1);
        assertThat(stats.byStatus().get("ACTIVE")).isGreaterThanOrEqualTo(1);
        assertThat(stats.daily()).hasSize(30);

        var detail = service.get(car.getId());
        assertThat(detail.reports()).hasSize(2);
    }
}
