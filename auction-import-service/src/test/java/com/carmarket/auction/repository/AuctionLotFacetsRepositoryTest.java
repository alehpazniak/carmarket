package com.carmarket.auction.repository;

import com.carmarket.auction.dto.LotFacetsResponse;
import com.carmarket.auction.entity.AuctionLot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The /aukcje dropdowns must only offer choices that give results: facets group case-insensitively, count only
 * listed lots, and every facet's count equals what GET /lots returns for it (exact make/model match).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuctionLotFacetsRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuctionLotRepository lots;

    private int lotNumber;

    private void lot(String make, String model, AuctionLot.LotStatus status) {
        lotNumber++;
        lots.saveAndFlush(AuctionLot.builder()
            .vin("VIN" + lotNumber).make(make).model(model).year(2020)
            .source(AuctionLot.AuctionSource.COPART).lotNumber(String.valueOf(lotNumber))
            .status(status)
            .build());
    }

    @BeforeEach
    void lots() {
        lot("TOYOTA", "RAV4", AuctionLot.LotStatus.LIVE);
        lot("Toyota", "rav4 ", AuctionLot.LotStatus.LIVE);
        lot(" toyota", "Camry", AuctionLot.LotStatus.LIVE);
        lot("TOYOTA", "RAV4", AuctionLot.LotStatus.SOLD);
        lot("TOYOTA", "COROLLA", AuctionLot.LotStatus.EXPIRED);
        lot("MAZDA", "3", AuctionLot.LotStatus.LIVE);
        lot("MAZDA", "CX-30", AuctionLot.LotStatus.LIVE);
        lot("MAZDA", "323", AuctionLot.LotStatus.LIVE);
        lot("MAZDA", " ", AuctionLot.LotStatus.LIVE);
        lot("HONDA", "CIVIC", AuctionLot.LotStatus.REMOVED);
    }

    @Test
    void facetsGroupCaseInsensitivelyAndCountOnlyListedLots() {
        LotFacetsResponse facets = LotFacetsResponse.from(lots.findFacetRows());

        assertThat(facets.makes()).extracting(LotFacetsResponse.MakeFacet::make, LotFacetsResponse.MakeFacet::count)
            .containsExactly(tuple("MAZDA", 4L), tuple("TOYOTA", 3L));
        assertThat(facets.makes().get(0).models()).extracting(LotFacetsResponse.ModelFacet::model)
            .containsExactly("3", "323", "CX-30"); // the blank model counts towards MAZDA but isn't offered
        assertThat(facets.makes().get(1).models())
            .containsExactly(new LotFacetsResponse.ModelFacet("CAMRY", 1), new LotFacetsResponse.ModelFacet("RAV4", 2));
    }

    @Test
    void everyFacetCountMatchesTheListing() {
        LotFacetsResponse facets = LotFacetsResponse.from(lots.findFacetRows());

        for (LotFacetsResponse.MakeFacet make : facets.makes()) {
            assertThat(total(make.make(), null)).as(make.make()).isEqualTo(make.count());
            for (LotFacetsResponse.ModelFacet model : make.models()) {
                assertThat(total(make.make(), model.model())).as(make.make() + " " + model.model()).isEqualTo(model.count());
            }
        }
    }

    @Test
    void modelIsAnExactMatch() {
        assertThat(lots.searchLiveLots("MAZDA", "3", null, null, null, null, null, Pageable.unpaged()).getContent())
            .extracting(AuctionLot::getModel).containsExactly("3");
        assertThat(total(null, null)).isEqualTo(7);
    }

    private long total(String make, String model) {
        return lots.searchLiveLots(make, model, null, null, null, null, null, Pageable.unpaged()).getTotalElements();
    }
}
