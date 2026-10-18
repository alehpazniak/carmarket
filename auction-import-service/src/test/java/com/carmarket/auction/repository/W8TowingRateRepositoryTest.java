package com.carmarket.auction.repository;

import com.carmarket.auction.entity.W8Auction;
import com.carmarket.auction.entity.W8Port;
import com.carmarket.auction.entity.W8TowingRate;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * V5 seeds W8's towing price list. ddl-auto is off for the context (see SyncRunRepositoryTest: V1 doesn't
 * match an unrelated entity on a fresh database), so the W8 entity is validated on its own below.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class W8TowingRateRepositoryTest {

    private static final String LIST = "W8-2025-26";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private W8TowingRateRepository rates;

    @Test
    void v5SeedsTheWholePriceList() {
        assertThat(rates.count()).isEqualTo(725);
        assertThat(rates.countByPriceList(LIST)).isEqualTo(725);
    }

    @Test
    void knownYards() {
        assertThat(mainYard(W8Auction.COPART, "SOMERVILLE", "NJ")).containsExactly(Map.entry(W8Port.NEW_YORK, money(175)));
        assertThat(mainYard(W8Auction.IAAI, "GREENSBORO", "NC"))
            .containsOnly(Map.entry(W8Port.NORFOLK, money(325)), Map.entry(W8Port.SAVANNAH, money(325)));
        assertThat(mainYard(W8Auction.COPART, "BILLINGS", "MT"))
            .containsOnly(Map.entry(W8Port.SEATTLE, money(850)), Map.entry(W8Port.HOUSTON, money(1250)));
    }

    @Test
    void sublotsAndOtherFinders() {
        List<W8TowingRate> somerville = rates.findByPriceListAndAuctionAndYardKey(LIST, W8Auction.COPART, "SOMERVILLE");
        assertThat(somerville).extracting(W8TowingRate::getSublot).containsExactlyInAnyOrder(null, "Offsite South Amboy");
        assertThat(rates.findByPriceListAndYardKeyAndState(LIST, "BILLINGS", "MT"))
            .extracting(W8TowingRate::getAuction).contains(W8Auction.COPART, W8Auction.IAAI);
        assertThat(rates.findByPriceListAndAuctionAndState(LIST, W8Auction.COPART, "MT")).isNotEmpty();
        assertThat(rates.findByPriceListAndState(LIST, "MT")).hasSizeGreaterThan(2);
        assertThat(rates.findByPriceListAndYardKeyAndState("W8-1999-00", "BILLINGS", "MT")).isEmpty();
    }

    @Test
    void prefixSearch() {
        assertThat(rates.searchByYardKeyPrefix(LIST, W8Auction.COPART, "CHIC", PageRequest.of(0, 50)))
            .isNotEmpty()
            .allSatisfy(r -> {
                assertThat(r.getYardKey()).startsWith("CHIC");
                assertThat(r.getAuction()).isEqualTo(W8Auction.COPART);
            });
        assertThat(rates.searchByYardKeyPrefix(LIST, null, "BILLINGS", PageRequest.of(0, 50)))
            .extracting(W8TowingRate::getAuction).contains(W8Auction.COPART, W8Auction.IAAI);
    }

    /** What ddl-auto: validate does at startup, for this entity alone (CHAR(2) state, enums, numeric). */
    @Test
    void entityPassesSchemaValidation() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.JAKARTA_JDBC_URL, POSTGRES.getJdbcUrl())
            .applySetting(AvailableSettings.JAKARTA_JDBC_USER, POSTGRES.getUsername())
            .applySetting(AvailableSettings.JAKARTA_JDBC_PASSWORD, POSTGRES.getPassword())
            .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
            .build();
        try {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(W8TowingRate.class).buildMetadata();
            // Building the session factory runs the validation and throws on any mismatch
            assertThatCode(() -> metadata.buildSessionFactory().close()).doesNotThrowAnyException();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private Map<W8Port, BigDecimal> mainYard(W8Auction auction, String yardKey, String state) {
        return rates.findByPriceListAndAuctionAndYardKeyAndState(LIST, auction, yardKey, state).stream()
            .filter(r -> r.getSublot() == null)
            .collect(Collectors.toMap(W8TowingRate::getPort, r -> r.getPriceUsd().stripTrailingZeros()));
    }

    private static BigDecimal money(int value) {
        return BigDecimal.valueOf(value).stripTrailingZeros();
    }
}
