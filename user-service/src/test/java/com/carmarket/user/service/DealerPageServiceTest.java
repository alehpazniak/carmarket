package com.carmarket.user.service;

import com.carmarket.user.dto.DealerPageDtos.DayHoursRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
            "Katowice", "", null, null, null, null, null, null, null, null, null, null,
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
            null, null, null, null, null, null, true);
    }

    private static DealerPageRequest withImages(String logo, List<String> gallery) {
        return new DealerPageRequest("auto-kowalski", "Auto Kowalski", null, null, null, null, null, null, null, null,
            null, null, logo, null, gallery, null, null, null, true);
    }

    private static DealerPageRequest withHours(List<DayHoursRequest> hours) {
        return new DealerPageRequest("auto-kowalski", "Auto Kowalski", null, null, null, null, null, null, null, null,
            null, null, null, null, null, hours, null, null, true);
    }
}
