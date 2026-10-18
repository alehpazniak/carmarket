package com.carmarket.user.dto;

import com.carmarket.user.entity.DealerPage;
import com.carmarket.user.entity.DealerPage.DayHours;
import com.carmarket.user.entity.DealerPage.TeamMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the dealer page API. */
public final class DealerPageDtos {

    private DealerPageDtos() {
    }

    private static final String HHMM = "^([01]\\d|2[0-3]):[0-5]\\d$";
    private static final String HTTP_URL = "^https?://\\S+$";

    public record DayHoursRequest(
        @Min(1) @Max(7) int day,
        @NotBlank @Pattern(regexp = HHMM, message = "Format GG:MM") String open,
        @NotBlank @Pattern(regexp = HHMM, message = "Format GG:MM") String close
    ) {
    }

    public record TeamMemberRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 80) String role
    ) {
    }

    /** PUT /users/me/dealer-page: replaces the whole page. */
    public record DealerPageRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,38})[a-z0-9]$",
            message = "3–40 znaków: małe litery, cyfry i myślniki (nie na początku ani na końcu)")
        String slug,
        @NotBlank @Size(max = 120) String companyName,
        @Size(max = 150) String headline,
        @Size(max = 5000) String description,
        @Size(max = 80) String city,
        @Size(max = 150) String street,
        @Size(max = 10) String postalCode,
        // Both or neither (checked in DealerPageService); null = not on the dealer map
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Nieprawidłowy numer telefonu") String phone,
        @Size(max = 255) @Pattern(regexp = HTTP_URL, message = "Adres musi zaczynać się od http:// lub https://") String website,
        @Size(max = 255) @Pattern(regexp = HTTP_URL, message = "Adres musi zaczynać się od http:// lub https://") String facebookUrl,
        @Size(max = 255) @Pattern(regexp = HTTP_URL, message = "Adres musi zaczynać się od http:// lub https://") String instagramUrl,
        @Size(max = 255) @Pattern(regexp = HTTP_URL, message = "Adres musi zaczynać się od http:// lub https://") String youtubeUrl,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String bannerUrl,
        @Size(max = 12) List<@NotBlank @Size(max = 500) String> galleryUrls,
        @Size(max = 7) List<@Valid DayHoursRequest> openingHours,
        @Size(max = 20) List<@NotBlank @Size(max = 120) String> services,
        @Size(max = 20) List<@Valid TeamMemberRequest> team,
        boolean published
    ) {
    }

    /** The owner's own page, including the phone number. */
    public record DealerPageOwnerView(
        String slug,
        String companyName,
        String headline,
        String description,
        String city,
        String street,
        String postalCode,
        Double latitude,
        Double longitude,
        String phone,
        String website,
        String facebookUrl,
        String instagramUrl,
        String youtubeUrl,
        String logoUrl,
        String bannerUrl,
        List<String> galleryUrls,
        List<DayHours> openingHours,
        List<String> services,
        List<TeamMember> team,
        boolean published,
        LocalDateTime updatedAt
    ) {
        public static DealerPageOwnerView from(DealerPage p) {
            return new DealerPageOwnerView(p.getSlug(), p.getCompanyName(), p.getHeadline(), p.getDescription(),
                p.getCity(), p.getStreet(), p.getPostalCode(), p.getLatitude(), p.getLongitude(), p.getPhone(),
                p.getWebsite(), p.getFacebookUrl(), p.getInstagramUrl(), p.getYoutubeUrl(), p.getLogoUrl(),
                p.getBannerUrl(), nn(p.getGalleryUrls()), nn(p.getOpeningHours()), nn(p.getServices()), nn(p.getTeam()), p.isPublished(), p.getUpdatedAt());
        }
    }

    /**
     * GET /users/dealers/{slug}: what anyone may see. Hand-built allow-list like
     * {@link PublicUserProfileResponse}; the phone is only behind GET /users/dealers/{slug}/phone.
     */
    public record DealerPageView(
        UUID userId,
        String slug,
        String companyName,
        String headline,
        String description,
        String city,
        String street,
        String postalCode,
        Double latitude,
        Double longitude,
        boolean hasPhone,
        String website,
        String facebookUrl,
        String instagramUrl,
        String youtubeUrl,
        String logoUrl,
        String bannerUrl,
        List<String> galleryUrls,
        List<DayHours> openingHours,
        List<String> services,
        List<TeamMember> team,
        LocalDateTime memberSince
    ) {
        public static DealerPageView from(DealerPage p, LocalDateTime memberSince) {
            return new DealerPageView(p.getUserId(), p.getSlug(), p.getCompanyName(), p.getHeadline(),
                p.getDescription(), p.getCity(), p.getStreet(), p.getPostalCode(), p.getLatitude(), p.getLongitude(),
                p.getPhone() != null && !p.getPhone().isBlank(), p.getWebsite(), p.getFacebookUrl(),
                p.getInstagramUrl(), p.getYoutubeUrl(), p.getLogoUrl(), p.getBannerUrl(), nn(p.getGalleryUrls()),
                nn(p.getOpeningHours()), nn(p.getServices()), nn(p.getTeam()), memberSince);
        }
    }

    /** GET /users/dealers/by-user/{userId}: the dealer box on a listing page. */
    public record DealerCard(String slug, String companyName, String logoUrl, String city) {
        public static DealerCard from(DealerPage p) {
            return new DealerCard(p.getSlug(), p.getCompanyName(), p.getLogoUrl(), p.getCity());
        }
    }

    /**
     * GET /users/dealers/nearby: one marker on the dealer map. Hand-built allow-list like {@link DealerCard}:
     * no phone, no user id.
     */
    public record DealerMapItem(
        String slug,
        String companyName,
        String logoUrl,
        String city,
        String street,
        String postalCode,
        double latitude,
        double longitude,
        double distanceKm
    ) {
        public static DealerMapItem from(DealerPage p, double distanceKm) {
            return new DealerMapItem(p.getSlug(), p.getCompanyName(), p.getLogoUrl(), p.getCity(), p.getStreet(),
                p.getPostalCode(), p.getLatitude(), p.getLongitude(), Math.round(distanceKm * 10) / 10.0);
        }
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }
}
