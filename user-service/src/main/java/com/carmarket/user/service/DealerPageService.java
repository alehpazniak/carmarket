package com.carmarket.user.service;

import com.carmarket.user.dto.DealerPageDtos.DayHoursRequest;
import com.carmarket.user.dto.DealerPageDtos.DealerMapItem;
import com.carmarket.user.dto.DealerPageDtos.DealerPageRequest;
import com.carmarket.user.entity.DealerPage;
import com.carmarket.user.entity.DealerPage.DayHours;
import com.carmarket.user.entity.DealerPage.TeamMember;
import com.carmarket.user.entity.UserProfile;
import com.carmarket.user.entity.UserProfile.UserRole;
import com.carmarket.user.repository.DealerPageRepository;
import com.carmarket.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Public dealer pages. The role is read from the profile (kept in sync by user.role-changed),
 * not from X-User-Roles, which the gateway signature doesn't cover.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealerPageService {

    /** Path segments the frontend uses under /dealer/{slug}, plus a few that would only confuse. */
    static final Set<String> RESERVED_SLUGS = Set.of(
        "admin", "api", "dealer", "dealers", "panel", "panel-dealera", "nowy", "nowa", "new", "edit",
        "oferta", "kontakt", "o-nas", "avtovo", "otomoto", "support", "pomoc", "by-user", "nearby");

    static final double EARTH_RADIUS_KM = 6371.0088;
    static final int MIN_RADIUS_KM = 1;
    static final int MAX_RADIUS_KM = 300;
    static final int MAX_NEARBY_LIMIT = 200;

    private final DealerPageRepository pages;
    private final UserProfileRepository profiles;

    /** A published page of an active DEALER/ADMIN, with the owner's profile; empty otherwise. */
    @Transactional(readOnly = true)
    public Optional<Visible> findPublic(String slug) {
        if (slug == null) return Optional.empty();
        return pages.findBySlug(slug.toLowerCase())
            .filter(DealerPage::isPublished)
            .flatMap(page -> profiles.findById(page.getUserId())
                .filter(DealerPageService::canHavePage)
                .map(profile -> new Visible(page, profile)));
    }

    /** The visible page of a seller, for the dealer box on their listings. */
    @Transactional(readOnly = true)
    public Optional<Visible> findPublicByUserId(UUID userId) {
        return pages.findById(userId)
            .filter(DealerPage::isPublished)
            .flatMap(page -> profiles.findById(userId)
                .filter(DealerPageService::canHavePage)
                .map(profile -> new Visible(page, profile)));
    }

    /** The business phone of a visible page, if it has one. */
    @Transactional(readOnly = true)
    public Optional<String> findPublicPhone(String slug) {
        return findPublic(slug)
            .map(v -> v.page().getPhone())
            .filter(phone -> !phone.isBlank());
    }

    /**
     * Visible pages with coordinates within {@code radiusKm} of a point, nearest first, at most {@code limit}.
     * A bounding-box query narrows the candidates (no PostGIS); the exact haversine distance decides.
     */
    @Transactional(readOnly = true)
    public List<DealerMapItem> findNearby(double lat, double lng, int radiusKm, int limit) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90 || !Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowe współrzędne");
        }
        if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Promień musi wynosić od " + MIN_RADIUS_KM + " do " + MAX_RADIUS_KM + " km");
        }
        if (limit < 1 || limit > MAX_NEARBY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit musi wynosić od 1 do " + MAX_NEARBY_LIMIT);
        }
        double latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double cosLat = Math.cos(Math.toRadians(lat));
        // Near the poles (or across the antimeridian) the box just spans every longitude
        double lngDelta = cosLat < 1e-6 ? 180 : Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cosLat));
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;
        if (minLng < -180 || maxLng > 180) {
            minLng = -180;
            maxLng = 180;
        }
        List<Object[]> rows = pages.findPublishedInBox(Math.max(-90, lat - latDelta), Math.min(90, lat + latDelta),
            minLng, maxLng, List.of(UserRole.DEALER, UserRole.ADMIN));

        List<DealerMapItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            DealerPage page = (DealerPage) row[0];
            UserProfile profile = (UserProfile) row[1];
            // The query already filters these; the same rules as findPublic, so they can't drift apart
            if (!page.isPublished() || !canHavePage(profile)
                || page.getLatitude() == null || page.getLongitude() == null) continue;
            double distance = distanceKm(lat, lng, page.getLatitude(), page.getLongitude());
            // The epsilon keeps a dealer exactly on the circle despite floating-point rounding
            if (distance <= radiusKm + 1e-9) result.add(DealerMapItem.from(page, distance));
        }
        result.sort(Comparator.comparingDouble(DealerMapItem::distanceKm).thenComparing(DealerMapItem::slug));
        return result.size() > limit ? List.copyOf(result.subList(0, limit)) : result;
    }

    /** Great-circle distance (haversine) in km. */
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Slug of the user's page if it is publicly visible (for the link on the public profile / listing page). */
    @Transactional(readOnly = true)
    public Optional<String> publishedSlug(UserProfile profile) {
        if (!canHavePage(profile)) return Optional.empty();
        return pages.findById(profile.getId()).filter(DealerPage::isPublished).map(DealerPage::getSlug);
    }

    @Transactional(readOnly = true)
    public Optional<DealerPage> findOwn(UUID userId) {
        requireDealer(userId);
        return pages.findById(userId);
    }

    @Transactional
    public DealerPage save(UUID userId, DealerPageRequest req) {
        requireDealer(userId);
        String slug = req.slug().toLowerCase();
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten adres strony jest zarezerwowany");
        }
        if (pages.existsBySlugAndUserIdNot(slug, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten adres strony jest już zajęty");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj obie współrzędne albo żadnej");
        }
        Pattern ownMedia = ownMediaPattern(userId);
        checkMedia(ownMedia, req.logoUrl());
        checkMedia(ownMedia, req.bannerUrl());
        List<String> gallery = req.galleryUrls() == null ? List.of() : req.galleryUrls();
        gallery.forEach(url -> checkMedia(ownMedia, url));

        DealerPage page = pages.findById(userId).orElseGet(() -> {
            DealerPage p = new DealerPage();
            p.setUserId(userId);
            return p;
        });
        page.setSlug(slug);
        page.setCompanyName(req.companyName().trim());
        page.setHeadline(blankToNull(req.headline()));
        page.setDescription(blankToNull(req.description()));
        page.setCity(blankToNull(req.city()));
        page.setStreet(blankToNull(req.street()));
        page.setPostalCode(blankToNull(req.postalCode()));
        page.setLatitude(req.latitude());
        page.setLongitude(req.longitude());
        page.setPhone(blankToNull(req.phone()));
        page.setWebsite(blankToNull(req.website()));
        page.setFacebookUrl(blankToNull(req.facebookUrl()));
        page.setInstagramUrl(blankToNull(req.instagramUrl()));
        page.setYoutubeUrl(blankToNull(req.youtubeUrl()));
        page.setLogoUrl(blankToNull(req.logoUrl()));
        page.setBannerUrl(blankToNull(req.bannerUrl()));
        page.setGalleryUrls(List.copyOf(gallery));
        page.setOpeningHours(openingHours(req.openingHours()));
        page.setServices(req.services() == null ? List.of()
            : req.services().stream().map(String::trim).toList());
        page.setTeam(req.team() == null ? List.of()
            : req.team().stream().map(m -> new TeamMember(m.name().trim(), blankToNull(m.role()))).toList());
        page.setPublished(req.published());
        try {
            DealerPage saved = pages.saveAndFlush(page);
            log.info("Dealer page saved: user={} slug={} published={}", userId, slug, saved.isPublished());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Two dealers claiming the same slug at the same time
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten adres strony jest już zajęty");
        }
    }

    private void requireDealer(UUID userId) {
        UserProfile profile = profiles.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil nie istnieje"));
        if (!canHavePage(profile)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Strona dealera jest dostępna tylko dla kont dealerów");
        }
    }

    static boolean canHavePage(UserProfile profile) {
        return profile.isActive() && (profile.getRole() == UserRole.DEALER || profile.getRole() == UserRole.ADMIN);
    }

    /** Images uploaded through car-service's /cars/dealer-media, under this dealer's own prefix only. */
    static Pattern ownMediaPattern(UUID userId) {
        return Pattern.compile("^https://[a-z0-9.-]+\\.amazonaws\\.com/dealers/" + userId
            + "/[A-Za-z0-9-]+\\.(jpg|png|webp)$");
    }

    private static void checkMedia(Pattern ownMedia, String url) {
        if (url != null && !url.isBlank() && !ownMedia.matcher(url).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy adres zdjęcia");
        }
    }

    private static List<DayHours> openingHours(List<DayHoursRequest> hours) {
        if (hours == null) return List.of();
        Set<Integer> days = new HashSet<>();
        for (DayHoursRequest h : hours) {
            if (!days.add(h.day())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Każdy dzień tygodnia może wystąpić tylko raz");
            }
            if (h.open().compareTo(h.close()) >= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Godzina zamknięcia musi być późniejsza niż otwarcia");
            }
        }
        return hours.stream()
            .sorted(Comparator.comparingInt(DayHoursRequest::day))
            .map(h -> new DayHours(h.day(), h.open(), h.close()))
            .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record Visible(DealerPage page, UserProfile profile) {
    }
}
