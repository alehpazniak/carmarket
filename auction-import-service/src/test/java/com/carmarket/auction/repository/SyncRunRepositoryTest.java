package com.carmarket.auction.repository;

import com.carmarket.auction.entity.SyncRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4 round-trips every SyncRun column and the latest-run query picks one row per parser.
 * Validates the whole schema on a fresh database (V6 renamed V1's auction_lot_images.image_url).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SyncRunRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SyncRunRepository runs;

    private SyncRun run(String parser, Instant startedAt, String error) {
        SyncRun run = SyncRun.start(parser, "SCHEDULED");
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusSeconds(5));
        run.setError(error);
        return runs.saveAndFlush(run);
    }

    @Test
    void everyColumnRoundTrips() {
        SyncRun run = SyncRun.start("ApibaraAuctionParser", "MANUAL");
        run.setFinishedAt(Instant.now());
        run.setLotsFetched(10);
        run.setLotsAdded(4);
        run.setLotsUpdated(3);
        run.setApiRequests(2);
        run.setQuotaLimit(100);
        run.setQuotaUsed(60);
        run.setQuotaRemaining(40);
        run.setQuotaPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
        run.setQuotaPeriodEnd(Instant.parse("2026-10-31T23:59:59Z"));
        run.setQuotaObservedAt(Instant.parse("2026-10-21T10:00:00Z"));
        run.setQuotaKeyFingerprint("0123456789ab");
        runs.saveAndFlush(run);

        SyncRun loaded = runs.findById(run.getId()).orElseThrow();
        assertThat(loaded.getTrigger()).isEqualTo("MANUAL");
        assertThat(loaded.getLotsAdded()).isEqualTo(4);
        assertThat(loaded.getFinishedAt()).isNotNull();
        assertThat(loaded.getApiRequests()).isEqualTo(2);
        assertThat(loaded.getQuotaRemaining()).isEqualTo(40);
        assertThat(loaded.getQuotaPeriodStart()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(loaded.getQuotaKeyFingerprint()).isEqualTo("0123456789ab");
    }

    @Test
    void latestPersistedQuotaSnapshotOfTheKey() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        SyncRun older = run("ApibaraAuctionParser", now.minus(3, ChronoUnit.HOURS), null);
        older.setQuotaRemaining(50);
        older.setQuotaObservedAt(now.minus(3, ChronoUnit.HOURS));
        older.setQuotaKeyFingerprint("aaaaaaaaaaaa");
        runs.saveAndFlush(older);
        SyncRun newer = run("ApibaraAuctionParser", now.minus(2, ChronoUnit.HOURS), null);
        newer.setQuotaRemaining(48);
        newer.setQuotaObservedAt(now.minus(2, ChronoUnit.HOURS));
        newer.setQuotaKeyFingerprint("aaaaaaaaaaaa");
        runs.saveAndFlush(newer);
        SyncRun otherKey = run("ApibaraAuctionParser", now.minus(1, ChronoUnit.HOURS), null);
        otherKey.setQuotaRemaining(0);
        otherKey.setQuotaObservedAt(now.minus(1, ChronoUnit.HOURS));
        otherKey.setQuotaKeyFingerprint("bbbbbbbbbbbb");
        runs.saveAndFlush(otherKey);
        run("ApibaraAuctionParser", now, "no answer"); // no snapshot

        assertThat(runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc("aaaaaaaaaaaa"))
            .map(SyncRun::getQuotaRemaining).contains(48);
        assertThat(runs.findFirstByQuotaKeyFingerprintAndQuotaObservedAtIsNotNullOrderByQuotaObservedAtDesc("cccccccccccc")).isEmpty();
    }

    @Autowired
    private SyncStreamRepository streams;

    @Test
    void spentRequestsAndTheDailyRunCheck() {
        Instant morning = Instant.parse("2026-10-26T07:00:00Z");
        SyncRun yesterday = SyncRun.start("FeedSync", "SCHEDULED");
        yesterday.setStartedAt(morning.minus(1, ChronoUnit.DAYS));
        yesterday.setApiRequests(7);
        runs.saveAndFlush(yesterday);
        SyncRun today = SyncRun.start("FeedSync", "SCHEDULED");
        today.setStartedAt(morning.plusSeconds(5));
        today.setApiRequests(3);
        runs.saveAndFlush(today);
        SyncRun running = SyncRun.start("FeedSync", "MANUAL"); // api_requests not known yet
        running.setStartedAt(morning.plusSeconds(600));
        runs.saveAndFlush(running);

        assertThat(runs.sumApiRequestsSince(morning.minus(2, ChronoUnit.DAYS))).isEqualTo(10);
        assertThat(runs.sumApiRequestsSince(morning)).isEqualTo(3);
        assertThat(runs.sumApiRequestsSince(morning.plus(1, ChronoUnit.DAYS))).isZero();
        assertThat(runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual("FeedSync", "SCHEDULED", morning)).isTrue();
        assertThat(runs.existsByParserAndTriggerAndStartedAtGreaterThanEqual("FeedSync", "SCHEDULED", morning.plusSeconds(60))).isFalse();
    }

    @Test
    void dayStreamsRoundTripAndOldDaysAreDeleted() {
        for (String key : new String[]{"open:2026-10-24", "open:2026-10-25", "open:2026-10-26", "outcomes"}) {
            com.carmarket.auction.entity.SyncStream stream = new com.carmarket.auction.entity.SyncStream(key);
            stream.setCursor("eyJ2ZWhpY2xlcy5hZCI6IjIwMjYtMTAtMjYgMDk6MDA6MDAifQ");
            stream.setLastTotal(18437);
            stream.setPagesDone(3);
            stream.setSequence(34414178L);
            streams.saveAndFlush(stream);
        }

        assertThat(streams.deleteDaysBefore("open:2026-10-25")).isEqualTo(1);
        assertThat(streams.findDays()).extracting(com.carmarket.auction.entity.SyncStream::getStreamKey)
            .containsExactly("open:2026-10-25", "open:2026-10-26");
        assertThat(streams.findById("outcomes").orElseThrow().getSequence()).isEqualTo(34414178L);
        assertThat(streams.findById("open:2026-10-26").orElseThrow().getLastTotal()).isEqualTo(18437);
    }

    @Test
    void latestRunPerParser() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        run("ApibaraAuctionParser", now.minus(1, ChronoUnit.HOURS), null);
        run("ApibaraAuctionParser", now, "quota exceeded");
        run("CopartParser", now.minus(2, ChronoUnit.HOURS), null);

        assertThat(runs.findLatestPerParser())
            .extracting(SyncRun::getParser, SyncRun::getError)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("ApibaraAuctionParser", "quota exceeded"),
                org.assertj.core.groups.Tuple.tuple("CopartParser", null));
    }
}
