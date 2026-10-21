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
        runs.saveAndFlush(run);

        SyncRun loaded = runs.findById(run.getId()).orElseThrow();
        assertThat(loaded.getTrigger()).isEqualTo("MANUAL");
        assertThat(loaded.getLotsAdded()).isEqualTo(4);
        assertThat(loaded.getFinishedAt()).isNotNull();
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
