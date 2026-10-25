package com.carmarket.auction.service;

import com.carmarket.auction.dto.LotLookupResponse;
import com.carmarket.auction.dto.LotLookupResponse.Kind;
import com.carmarket.auction.entity.AuctionLot;
import com.carmarket.auction.repository.AuctionLotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** "Numer lota, VIN lub link" on /aukcje against our own lots: no Apibara request involved. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LotLookupService.class)
class LotLookupServiceTest {

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
    @Autowired
    private LotLookupService lookup;

    private AuctionLot lot(AuctionLot.AuctionSource source, String lotNumber, String vin, AuctionLot.LotStatus status,
                           String auctionDate, List<String> images) {
        return lots.saveAndFlush(AuctionLot.builder()
            .vin(vin).make("MAZDA").model("CX-5").year(2021)
            .source(source).lotNumber(lotNumber).status(status)
            .auctionDate(auctionDate == null ? null : Instant.parse(auctionDate))
            .imageUrls(new java.util.ArrayList<>(images))
            .build());
    }

    private AuctionLot copart(String lotNumber, String vin) {
        return lot(AuctionLot.AuctionSource.COPART, lotNumber, vin, AuctionLot.LotStatus.LIVE, "2026-10-27T15:00:00Z", List.of());
    }

    @Test
    void aLotNumber() {
        AuctionLot lot = copart("61747696", "JM3KFBDM6M1463543");

        LotLookupResponse found = lookup.lookup(" 61747696 ");

        assertThat(found.kind()).isEqualTo(Kind.LOT_NUMBER);
        assertThat(found.ids()).containsExactly(lot.getId());
        assertThat(found.identifier()).isEqualTo("61747696");
    }

    @Test
    void aVinWhateverItsCase() {
        AuctionLot lot = copart("61747696", "JM3KFBDM6M1463543");

        LotLookupResponse found = lookup.lookup("jm3kfbdm6m1463543");

        assertThat(found.kind()).isEqualTo(Kind.VIN);
        assertThat(found.ids()).containsExactly(lot.getId());
        assertThat(found.identifier()).isEqualTo("JM3KFBDM6M1463543");
    }

    /** the lot number from the link, Copart only: an IAAI lot with the same number is another car */
    @Test
    void aCopartLink() {
        AuctionLot lot = copart("61747696", "JM3KFBDM6M1463543");
        lot(AuctionLot.AuctionSource.IAAI, "61747696", "1HGCM82633A004352", AuctionLot.LotStatus.LIVE, null, List.of());

        LotLookupResponse found = lookup.lookup("https://www.copart.com/lot/61747696/salvage-2021-mazda-cx-5-grand-touring-il-chicago-south");

        assertThat(found.kind()).isEqualTo(Kind.COPART_URL);
        assertThat(found.ids()).containsExactly(lot.getId());
        assertThat(found.identifier()).isNull(); // a link goes through Apibara's resolver when we don't have it
    }

    /** an IAAI link carries the item id, which only the image keys hold; the stock (lot) number is another number */
    @Test
    void anIaaiLinkMatchesThroughTheImageKeys() {
        AuctionLot lot = lot(AuctionLot.AuctionSource.IAAI, "45571503", "JM3KFBDM7K1621899", AuctionLot.LotStatus.LIVE,
            "2026-10-27T15:00:00Z", List.of("https://vis.iaai.com/resizer?imageKeys=46069832~SID~I1&width=400&height=300"));
        lot(AuctionLot.AuctionSource.IAAI, "45571504", "JM3KFBDM7K1621800", AuctionLot.LotStatus.LIVE, null,
            List.of("https://vis.iaai.com/resizer?imageKeys=460698321~SID~I1&width=400&height=300")); // a longer id

        LotLookupResponse found = lookup.lookup("https://www.iaai.com/VehicleDetail/46069832~US");

        assertThat(found.kind()).isEqualTo(Kind.IAAI_URL);
        assertThat(found.ids()).containsExactly(lot.getId());
        assertThat(lookup.lookup("https://www.iaai.com/VehicleDetail/45571503~US").ids()).isEmpty();
    }

    @Test
    void severalMatchesLiveFirstThenTheLatestAuction() {
        AuctionLot sold = lot(AuctionLot.AuctionSource.COPART, "50000001", "JM3KFBDM6M1463543", AuctionLot.LotStatus.SOLD,
            "2026-10-30T15:00:00Z", List.of());
        AuctionLot olderLive = lot(AuctionLot.AuctionSource.COPART, "50000002", "JM3KFBDM6M1463543", AuctionLot.LotStatus.LIVE,
            "2026-10-20T15:00:00Z", List.of());
        AuctionLot newerLive = lot(AuctionLot.AuctionSource.IAAI, "50000003", "JM3KFBDM6M1463543", AuctionLot.LotStatus.LIVE,
            "2026-10-28T15:00:00Z", List.of());
        AuctionLot undated = lot(AuctionLot.AuctionSource.IAAI, "50000004", "JM3KFBDM6M1463543", AuctionLot.LotStatus.LIVE,
            null, List.of());

        assertThat(lookup.lookup("JM3KFBDM6M1463543").ids())
            .containsExactly(newerLive.getId(), olderLive.getId(), undated.getId(), sold.getId());
    }

    @Test
    void notFoundOrNothingToLookUp() {
        copart("61747696", "JM3KFBDM6M1463543");

        assertThat(lookup.lookup("99999999")).isEqualTo(new LotLookupResponse(Kind.LOT_NUMBER, List.of(), "99999999"));
        // masked Copart VINs are stored as Apibara sends them: a full VIN doesn't find them, which is normal
        assertThat(lookup.lookup("1HGCM82633A004352").ids()).isEmpty();
        assertThat(lookup.lookup("https://www.ebay.com/itm/123").kind()).isEqualTo(Kind.URL);
        assertThat(lookup.lookup("mazda cx-5")).isEqualTo(new LotLookupResponse(Kind.UNKNOWN, List.of(), null));
        assertThat(lookup.lookup("  ").kind()).isEqualTo(Kind.UNKNOWN);
    }
}
