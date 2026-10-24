package com.carmarket.auction.service;

import com.carmarket.auction.dto.AuctionLotChangedEvent;
import com.carmarket.auction.config.ClockConfig;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The sync upsert against Postgres with every migration applied (V6 on an empty database, ddl-auto validate):
 * idempotent by content hash, unique per (source, lot_number), events only after commit.
 * Not wrapped in a test transaction, so the service's own transaction really commits or rolls back.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuctionLotService.class, AuctionLotEventPublisher.class, ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AuctionLotServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AuctionLotService service;

    @Autowired
    private AuctionLotRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auction_lot_images");
        jdbc.update("DELETE FROM auction_lots");
    }

    private static AuctionLot lot(AuctionLot.AuctionSource source, String lotNumber) {
        return AuctionLot.builder()
            .vin("5UXTR9C54KLD98072").make("BMW").model("X3").year(2019)
            .source(source).lotNumber(lotNumber)
            .auctionPrice(new BigDecimal("7200")).primaryDamage("FRONT END")
            .auctionLocation("Tampa South (FL)")
            .auctionDate(Instant.parse("2026-08-07T14:00:00Z"))
            .imageUrls(new ArrayList<>(List.of("https://example.com/1.jpg")))
            .rawData("{\"lot_number\":\"" + lotNumber + "\",\"auction\":{\"diff_minutes\":-30646}}")
            .build();
    }

    @Test
    void sameDataTwiceIsOneInsertAndNoUpdate() {
        assertThat(service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "58456596"))))
            .isEqualTo(new AuctionLotService.SyncCounts(1, 0));

        // a fresh raw JSON (countdowns change on every request) is not a change
        AuctionLot again = lot(AuctionLot.AuctionSource.COPART, "58456596");
        again.setRawData("{\"auction\":{\"diff_minutes\":-30000}}");
        assertThat(service.saveOrUpdateLots(List.of(again))).isEqualTo(new AuctionLotService.SyncCounts(0, 0));

        assertThat(repository.count()).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(eq("auction.lot.updated"), anyString(), any());
    }

    @Test
    void rescheduledAuctionGetsItsNewDateAndEveryMappedField() {
        service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "58456596")));
        String firstHash = jdbc.queryForObject("SELECT content_hash FROM auction_lots", String.class);
        clearInvocations(kafkaTemplate);

        AuctionLot moved = lot(AuctionLot.AuctionSource.COPART, "58456596");
        moved.setAuctionDate(Instant.parse("2026-08-14T15:30:00Z"));
        moved.setPrimaryDamage("REAR END");
        moved.setVin("5UXTR9C54KLD98073");
        moved.setImageUrls(new ArrayList<>(List.of("https://example.com/2.jpg", "https://example.com/3.jpg")));
        assertThat(service.saveOrUpdateLots(List.of(moved))).isEqualTo(new AuctionLotService.SyncCounts(0, 1));

        AuctionLot stored = tx.execute(s -> {
            AuctionLot l = repository.findByLotNumberAndSource("58456596", AuctionLot.AuctionSource.COPART).orElseThrow();
            l.getImageUrls().size();
            return l;
        });
        assertThat(stored.getAuctionDate()).isEqualTo(Instant.parse("2026-08-14T15:30:00Z"));
        assertThat(stored.getPrimaryDamage()).isEqualTo("REAR END");
        assertThat(stored.getVin()).isEqualTo("5UXTR9C54KLD98073");
        assertThat(stored.getImageUrls()).containsExactly("https://example.com/2.jpg", "https://example.com/3.jpg");
        assertThat(stored.getContentHash()).hasSize(64).isNotEqualTo(firstHash);
        assertThat(stored.getRawData()).contains("58456596");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("auction.lot.updated"), eq(stored.getId().toString()), event.capture());
        assertThat(event.getValue()).isEqualTo(new AuctionLotChangedEvent(stored.getId(),
            AuctionLot.AuctionSource.COPART, "58456596", AuctionLot.LotStatus.LIVE,
            Instant.parse("2026-08-14T15:30:00Z"), AuctionLotChangedEvent.ChangeType.UPDATED));
    }

    @Test
    void sameLotNumberAtCopartAndIaaiAreTwoLots() {
        service.saveOrUpdateLots(List.of(
            lot(AuctionLot.AuctionSource.COPART, "45008746"),
            lot(AuctionLot.AuctionSource.IAAI, "45008746")));

        assertThat(repository.count()).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO auction_lots (id, vin, make, model, year, source, lot_number, status)
            VALUES (gen_random_uuid(), 'X', 'BMW', 'X3', 2019, 'IAAI', '45008746', 'LIVE')"""))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFailingPageIsRolledBackAndAnnouncesNothing() {
        AuctionLot broken = lot(AuctionLot.AuctionSource.COPART, "2");
        broken.setMake(null); // NOT NULL

        assertThatThrownBy(() -> service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "1"), broken)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.count()).isZero();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    private AuctionLot stored(String lotNumber) {
        return repository.findByLotNumberAndSource(lotNumber, AuctionLot.AuctionSource.COPART).orElseThrow();
    }

    private static AuctionLot sold(String lotNumber) {
        AuctionLot lot = lot(AuctionLot.AuctionSource.COPART, lotNumber);
        lot.setStatus(AuctionLot.LotStatus.SOLD);
        lot.setSoldPrice(new BigDecimal("7900"));
        lot.setEndedAt(Instant.parse("2026-08-07T15:00:00Z"));
        lot.setVehicleType("AUTOMOBILE");
        return lot;
    }

    @Test
    void aFinalResultIsNotRevertedByAnOlderRecord() {
        service.saveOrUpdateLots(List.of(sold("1")));

        // the same auction again as open (a stale page), or ended without a result: SOLD stays
        service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "1")));
        AuctionLot noResult = lot(AuctionLot.AuctionSource.COPART, "1");
        noResult.setStatus(AuctionLot.LotStatus.EXPIRED);
        service.saveOrUpdateLots(List.of(noResult));

        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(stored("1").getSoldPrice()).isEqualByComparingTo("7900");
        assertThat(stored("1").getVehicleType()).isEqualTo("AUTOMOBILE");
    }

    @Test
    void aRelistedLotTakesTheFeedsState() {
        service.saveOrUpdateLots(List.of(sold("1")));

        AuctionLot relisted = lot(AuctionLot.AuctionSource.COPART, "1");
        relisted.setAuctionDate(Instant.parse("2026-09-01T14:00:00Z"));
        service.saveOrUpdateLots(List.of(relisted));

        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.LIVE);
        assertThat(stored("1").getSoldPrice()).isNull();
        assertThat(stored("1").getEndedAt()).isNull();
    }

    @Test
    void theFeedReplacesExpiredAndASaleThatFellThrough() {
        AuctionLot expired = lot(AuctionLot.AuctionSource.COPART, "1");
        expired.setStatus(AuctionLot.LotStatus.EXPIRED);
        service.saveOrUpdateLots(List.of(expired));

        service.saveOrUpdateLots(List.of(sold("1")));
        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);

        AuctionLot fellThrough = sold("1");
        fellThrough.setStatus(AuctionLot.LotStatus.UNSOLD);
        fellThrough.setSoldPrice(null);
        service.saveOrUpdateLots(List.of(fellThrough));
        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.UNSOLD);
    }

    @Test
    void everyLotOfAPageIsMarkedSeenEvenWhenUnchanged() {
        service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "1")));
        jdbc.update("UPDATE auction_lots SET last_seen_at = TIMESTAMPTZ '2026-01-01 00:00:00+00'");

        assertThat(service.saveOrUpdateLots(List.of(lot(AuctionLot.AuctionSource.COPART, "1"), lot(AuctionLot.AuctionSource.COPART, "2"))))
            .isEqualTo(new AuctionLotService.SyncCounts(1, 0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auction_lots WHERE last_seen_at > TIMESTAMPTZ '2026-01-01 00:00:00+00'",
            Integer.class)).isEqualTo(2);
    }

    private static com.carmarket.auction.sync.LotOutcome outcome(String lotNumber, AuctionLot.LotStatus status, String price) {
        return new com.carmarket.auction.sync.LotOutcome(AuctionLot.AuctionSource.COPART, lotNumber, status,
            price == null ? null : new BigDecimal(price), null, null);
    }

    @Test
    void aConfirmedSaleKeepsTheApprovalPriceAndOtherLotsAreIgnored() {
        AuctionLot onApproval = lot(AuctionLot.AuctionSource.COPART, "1");
        onApproval.setStatus(AuctionLot.LotStatus.EXPIRED);
        onApproval.setSoldPrice(new BigDecimal("6500"));
        service.saveOrUpdateLots(List.of(onApproval));
        clearInvocations(kafkaTemplate);

        int changed = service.applyOutcomes(List.of(
            outcome("1", AuctionLot.LotStatus.SOLD, null),
            outcome("not-stored", AuctionLot.LotStatus.SOLD, "900")));

        assertThat(changed).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(stored("1").getSoldPrice()).isEqualByComparingTo("6500");
        assertThat(stored("1").getEndedAt()).isEqualTo(Instant.parse("2026-08-07T14:00:00Z")); // the auction, as nothing better came
        verify(kafkaTemplate, times(1)).send(eq("auction.lot.updated"), anyString(), any());

        // the same event again changes nothing
        assertThat(service.applyOutcomes(List.of(outcome("1", AuctionLot.LotStatus.SOLD, null)))).isZero();
    }

    @Test
    void removedNeverReplacesAResultAndNotSoldDropsThePrice() {
        service.saveOrUpdateLots(List.of(sold("1"), lot(AuctionLot.AuctionSource.COPART, "2")));

        service.applyOutcomes(List.of(
            outcome("1", AuctionLot.LotStatus.REMOVED, null),
            outcome("2", AuctionLot.LotStatus.UNSOLD, null)));

        assertThat(stored("1").getStatus()).isEqualTo(AuctionLot.LotStatus.SOLD);
        assertThat(stored("2").getStatus()).isEqualTo(AuctionLot.LotStatus.UNSOLD);
        assertThat(stored("2").getSoldPrice()).isNull();
    }

    @Test
    void auctionDateIsStoredAsAnInstant() {
        AuctionLot warsaw = lot(AuctionLot.AuctionSource.COPART, "1");
        warsaw.setAuctionDate(java.time.OffsetDateTime.parse("2026-08-07T16:00:00+02:00").toInstant());
        service.saveOrUpdateLots(List.of(warsaw));

        assertThat(jdbc.queryForObject("SELECT auction_date AT TIME ZONE 'UTC' FROM auction_lots", String.class))
            .startsWith("2026-08-07 14:00:00");
    }
}
