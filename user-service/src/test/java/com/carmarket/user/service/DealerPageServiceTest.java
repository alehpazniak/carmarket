package com.carmarket.user.service;

import com.carmarket.user.dto.DealerPageDtos.DayHoursRequest;
import com.carmarket.user.dto.DealerPageDtos.DealerMapItem;
import com.carmarket.user.dto.DealerPageDtos.DealerPageRequest;
import com.carmarket.user.dto.DealerPageDtos.DealerPageView;
import com.carmarket.user.dto.DealerPageDtos.TeamMemberRequest;
import com.carmarket.user.entity.DealerPage;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.entity.UserProfile.UserRole;
import com.carmarket.user.repository.DealerPageRepository;
import com.carmarket.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealerPageServiceTest {

    private final DealerPageRepository pages = mock(DealerPageRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final DealerPageService service = new DealerPageService(pages, profiles);

    private final UUID dealerId = UUID.randomUUID();
    private UserProfile dealer;

    @BeforeEach
    void setUp() {
        dealer = profile(dealerId, UserRole.DEALER, true);
        when(profiles.findById(dealerId)).thenReturn(Optional.of(dealer));
        when(pages.findById(dealerId)).thenReturn(Optional.empty());
        when(pages.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publicPageOnlyWhilePublishedActiveAndDealer() {
        DealerPage page = page(true);
        when(pages.findBySlug("auto-kowalski")).thenReturn(Optional.of(page));

        assertThat(service.findPublic("Auto-Kowalski")).isPresent();

        page.setPublished(false);
        assertThat(service.findPublic("auto-kowalski")).isEmpty();

        page.setPublished(true);
        dealer.setActive(false);
        assertThat(service.findPublic("auto-kowalski")).isEmpty();

        dealer.setActive(true);
        dealer.setRole(UserRole.USER);
        assertThat(service.findPublic("auto-kowalski")).isEmpty();
        assertThat(service.findPublicPhone("auto-kowalski")).isEmpty();
    }

    @Test
    void publicViewHidesThePhoneButSaysThereIsOne() {
        DealerPage page = page(true);
        page.setPhone("+48 600 100 200");
        DealerPageView view = DealerPageView.from(page, LocalDateTime.of(2023, 5, 1, 0, 0));

        assertThat(view.hasPhone()).isTrue();
        assertThat(view.toString()).doesNotContain("600 100 200");
        assertThat(view.memberSince().getYear()).isEqualTo(2023);
    }

    @Test
    void onlyDealersAndAdminsCanHaveAPage() {
        UUID userId = UUID.randomUUID();
        when(profiles.findById(userId)).thenReturn(Optional.of(profile(userId, UserRole.USER, true)));

        assertThatThrownBy(() -> service.save(userId, request("auto-kowalski")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.findOwn(userId)).isInstanceOf(ResponseStatusException.class);
        verify(pages, never()).saveAndFlush(any());
    }

    @Test
    void slugMustBeFreeAndNotReserved() {
        when(pages.existsBySlugAndUserIdNot("zajety", dealerId)).thenReturn(true);

        assertThatThrownBy(() -> service.save(dealerId, request("zajety")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.save(dealerId, request("oferta")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void imagesMustBeTheDealersOwnUploads() {
        String own = "https://bucket.s3.eu-central-1.amazonaws.com/dealers/" + dealerId + "/" + UUID.randomUUID() + ".png";
        String foreign = "https://bucket.s3.eu-central-1.amazonaws.com/dealers/" + UUID.randomUUID() + "/a.png";

        DealerPage saved = service.save(dealerId, withImages(own, List.of(own)));
        assertThat(saved.getLogoUrl()).isEqualTo(own);

        for (String bad : List.of(foreign, "https://evil.example.com/dealers/" + dealerId + "/a.png",
            "javascript:alert(1)", "https://bucket.s3.eu-central-1.amazonaws.com/dealers/" + dealerId + "/a.svg")) {
            assertThatThrownBy(() -> service.save(dealerId, withImages(bad, List.of())))
                .as(bad).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.save(dealerId, withImages(null, List.of(bad))))
                .as(bad).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void openingHoursAreSortedAndValidated() {
        DealerPage saved = service.save(dealerId, withHours(List.of(
            new DayHoursRequest(6, "10:00", "14:00"), new DayHoursRequest(1, "09:00", "18:00"))));
        assertThat(saved.getOpeningHours()).extracting(DealerPage.DayHours::day).containsExactly(1, 6);

        assertThatThrownBy(() -> service.save(dealerId, withHours(List.of(
            new DayHoursRequest(1, "09:00", "18:00"), new DayHoursRequest(1, "10:00", "12:00")))))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.save(dealerId, withHours(List.of(new DayHoursRequest(2, "18:00", "09:00")))))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void blankFieldsAreStoredAsNullAndSlugLowercased() {
        DealerPage saved = service.save(dealerId, new DealerPageRequest("Auto-Kowalski", "  Auto Kowalski  ", " ", null,
            "Katowice", "", null, null, null, null, null, null, null, null, null, null, null, null,
            List.of(" Import z USA "), List.of(new TeamMemberRequest(" Tomek ", " ")), true));

        assertThat(saved.getSlug()).isEqualTo("auto-kowalski");
        assertThat(saved.getCompanyName()).isEqualTo("Auto Kowalski");
        assertThat(saved.getHeadline()).isNull();
        assertThat(saved.getStreet()).isNull();
        assertThat(saved.getServices()).containsExactly("Import z USA");
        assertThat(saved.getTeam()).containsExactly(new DealerPage.TeamMember("Tomek", null));
        assertThat(saved.getGalleryUrls()).isEmpty();
    }

    @Test
    void publishedSlugIsOnlyForVisiblePages() {
        DealerPage page = page(true);
        when(pages.findById(dealerId)).thenReturn(Optional.of(page));
        assertThat(service.publishedSlug(dealer)).contains("auto-kowalski");

        assertThat(service.findPublicByUserId(dealerId)).isPresent();

        page.setPublished(false);
        assertThat(service.publishedSlug(dealer)).isEmpty();
        assertThat(service.findPublicByUserId(dealerId)).isEmpty();
    }

    // --- dealer map ---

    private static final double WARSAW_LAT = 52.2297, WARSAW_LNG = 21.0122;
    private static final double KRAKOW_LAT = 50.0647, KRAKOW_LNG = 19.9450;

    @Test
    void warsawToKrakowIsAbout252Km() {
        assertThat(DealerPageService.distanceKm(WARSAW_LAT, WARSAW_LNG, KRAKOW_LAT, KRAKOW_LNG))
            .isCloseTo(252, within(2.0));
        assertThat(DealerPageService.distanceKm(WARSAW_LAT, WARSAW_LNG, WARSAW_LAT, WARSAW_LNG)).isZero();
    }

    @Test
    void nearbyListsOnlyVisiblePagesWithCoordinates() {
        stubBox(
            row("widoczny", UserRole.DEALER, true, true, 52.25, 21.0),
            row("admin-page", UserRole.ADMIN, true, true, 52.24, 21.0),
            row("ukryty", UserRole.DEALER, true, false, 52.23, 21.0),
            row("nieaktywny", UserRole.DEALER, false, true, 52.23, 21.0),
            row("zwykly", UserRole.USER, true, true, 52.23, 21.0),
            row("bez-mapy", UserRole.DEALER, true, true, null, null));

        assertThat(service.findNearby(WARSAW_LAT, WARSAW_LNG, 50, 100))
            .extracting(DealerMapItem::slug)
            .containsExactlyInAnyOrder("widoczny", "admin-page");
    }

    @Test
    void nearbyAsksForDealersAndAdminsInABoxAroundThePoint() {
        stubBox();
        service.findNearby(WARSAW_LAT, WARSAW_LNG, 100, 100);

        ArgumentCaptor<Double> minLat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> minLng = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLng = ArgumentCaptor.forClass(Double.class);
        verify(pages).findPublishedInBox(minLat.capture(), maxLat.capture(), minLng.capture(), maxLng.capture(),
            eq(List.of(UserRole.DEALER, UserRole.ADMIN)));
        // 100 km ≈ 0.9° of latitude and ≈ 1.47° of longitude at Warsaw's latitude
        assertThat(maxLat.getValue() - WARSAW_LAT).isCloseTo(0.899, within(0.01));
        assertThat(WARSAW_LAT - minLat.getValue()).isCloseTo(0.899, within(0.01));
        assertThat(maxLng.getValue() - WARSAW_LNG).isCloseTo(1.47, within(0.02));
        assertThat(WARSAW_LNG - minLng.getValue()).isCloseTo(1.47, within(0.02));
    }

    @Test
    void nearbyFiltersByExactRadiusAndSortsByDistance() {
        // The box's corners are farther than the radius: the haversine distance decides
        stubBox(
            row("daleko", UserRole.DEALER, true, true, 52.2297, 21.75),    // ~50.3 km
            row("blisko", UserRole.DEALER, true, true, 52.2297, 21.10),    // ~6 km
            row("srodek", UserRole.DEALER, true, true, 52.40, 21.0122),    // ~18.9 km
            row("rog", UserRole.DEALER, true, true, 52.60, 21.55));        // ~55 km, inside the box

        List<DealerMapItem> result = service.findNearby(WARSAW_LAT, WARSAW_LNG, 50, 100);

        assertThat(result).extracting(DealerMapItem::slug).containsExactly("blisko", "srodek");
        assertThat(result.get(0).distanceKm()).isEqualTo(6.0);
        assertThat(result.get(1).distanceKm()).isEqualTo(18.9);
        assertThat(service.findNearby(WARSAW_LAT, WARSAW_LNG, 50, 1)).extracting(DealerMapItem::slug)
            .containsExactly("blisko");
    }

    @Test
    void nearbyRadiusBoundary() {
        double distance = DealerPageService.distanceKm(WARSAW_LAT, WARSAW_LNG, KRAKOW_LAT, KRAKOW_LNG);
        stubBox(row("krakow", UserRole.DEALER, true, true, KRAKOW_LAT, KRAKOW_LNG));

        assertThat(service.findNearby(WARSAW_LAT, WARSAW_LNG, (int) Math.ceil(distance), 100)).hasSize(1);
        assertThat(service.findNearby(WARSAW_LAT, WARSAW_LNG, (int) Math.floor(distance), 100)).isEmpty();
    }

    @Test
    void nearbyIncludesADealerExactlyOnTheRadius() {
        // Due north, exactly 50 km along the meridian
        double lat = WARSAW_LAT + Math.toDegrees(50 / DealerPageService.EARTH_RADIUS_KM);
        stubBox(row("na-granicy", UserRole.DEALER, true, true, lat, WARSAW_LNG));

        assertThat(service.findNearby(WARSAW_LAT, WARSAW_LNG, 50, 100)).extracting(DealerMapItem::distanceKm)
            .containsExactly(50.0);
    }

    @Test
    void nearbyExposesNoPhoneOrUserId() {
        Object[] withPhone = row("z-telefonem", UserRole.DEALER, true, true, 52.23, 21.01);
        DealerPage page = (DealerPage) withPhone[0];
        page.setPhone("+48 600 100 200");
        stubBox(withPhone);

        DealerMapItem item = service.findNearby(WARSAW_LAT, WARSAW_LNG, 10, 100).get(0);
        assertThat(item.toString()).doesNotContain("600 100 200").doesNotContain(page.getUserId().toString());
    }

    @Test
    void nearbyRejectsInvalidParameters() {
        for (double[] p : new double[][]{{91, 0}, {-91, 0}, {0, 181}, {0, -181}, {Double.NaN, 0}}) {
            assertBadRequest(() -> service.findNearby(p[0], p[1], 50, 100));
        }
        assertBadRequest(() -> service.findNearby(52, 21, 0, 100));
        assertBadRequest(() -> service.findNearby(52, 21, 301, 100));
        assertBadRequest(() -> service.findNearby(52, 21, 50, 0));
        assertBadRequest(() -> service.findNearby(52, 21, 50, 201));
        verify(pages, never()).findPublishedInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void coordinatesAreSavedAsAPair() {
        DealerPage saved = service.save(dealerId, withLocation(52.2297, 21.0122));
        assertThat(saved.getLatitude()).isEqualTo(52.2297);
        assertThat(saved.getLongitude()).isEqualTo(21.0122);

        DealerPage cleared = service.save(dealerId, withLocation(null, null));
        assertThat(cleared.getLatitude()).isNull();
        assertThat(cleared.getLongitude()).isNull();

        assertBadRequest(() -> service.save(dealerId, withLocation(52.2297, null)));
        assertBadRequest(() -> service.save(dealerId, withLocation(null, 21.0122)));
    }

    @Test
    void nearbyIsAReservedSlug() {
        assertThatThrownBy(() -> service.save(dealerId, request("nearby")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private void stubBox(Object[]... rows) {
        when(pages.findPublishedInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
            .thenReturn(List.of(rows));
    }

    private static Object[] row(String slug, UserRole role, boolean active, boolean published, Double lat, Double lng) {
        UUID id = UUID.randomUUID();
        DealerPage page = new DealerPage();
        page.setUserId(id);
        page.setSlug(slug);
        page.setCompanyName("Firma " + slug);
        page.setPublished(published);
        page.setLatitude(lat);
        page.setLongitude(lng);
        return new Object[]{page, profile(id, role, active)};
    }

    private static void assertBadRequest(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static DealerPageRequest withLocation(Double lat, Double lng) {
        return new DealerPageRequest("auto-kowalski", "Auto Kowalski", null, null, null, null, null, lat, lng, null,
            null, null, null, null, null, null, null, null, null, null, true);
    }

    private DealerPage page(boolean published) {
        DealerPage page = new DealerPage();
        page.setUserId(dealerId);
        page.setSlug("auto-kowalski");
        page.setCompanyName("Auto Kowalski");
        page.setPublished(published);
        return page;
    }

    private static UserProfile profile(UUID id, UserRole role, boolean active) {
        UserProfile p = new UserProfile();
        p.setId(id);
        p.setEmail(id + "@example.com");
        p.setRole(role);
        p.setActive(active);
        return p;
    }

    private static DealerPageRequest request(String slug) {
        return new DealerPageRequest(slug, "Auto Kowalski", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, true);
    }

    private static DealerPageRequest withImages(String logo, List<String> gallery) {
        return new DealerPageRequest("auto-kowalski", "Auto Kowalski", null, null, null, null, null, null, null, null,
            null, null, null, null, logo, null, gallery, null, null, null, true);
    }

    private static DealerPageRequest withHours(List<DayHoursRequest> hours) {
        return new DealerPageRequest("auto-kowalski", "Auto Kowalski", null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, hours, null, null, true);
    }
}
