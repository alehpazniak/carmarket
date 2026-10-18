package com.carmarket.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A dealer's public page (/dealer/{slug}), modeled on otomoto's dealer pages: "O nas", "Oferta"
 * (the dealer's ACTIVE listings from car-service) and "Kontakt". One per user; only shown while
 * {@code published}, the account is active and its role is DEALER or ADMIN. Pages with coordinates
 * also appear on the dealer map (GET /users/dealers/nearby).
 */
@Entity
@Table(name = "dealer_pages", indexes = @Index(name = "idx_dealer_pages_location", columnList = "latitude, longitude"))
@Getter
@Setter
@NoArgsConstructor
public class DealerPage {

    /** Same as UserProfile.id. */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(length = 150)
    private String headline;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 80)
    private String city;

    @Column(length = 150)
    private String street;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    /** WGS84, set from the dealer page editor (no server-side geocoding); both or neither. */
    private Double latitude;

    private Double longitude;

    /** Business number, shown only on "Wyświetl numer" (GET /users/dealers/{slug}/phone). */
    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String website;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 255)
    private String youtubeUrl;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_urls", columnDefinition = "jsonb")
    private List<String> galleryUrls = new ArrayList<>();

    /** Days without an entry are closed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private List<DayHours> openingHours = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> services = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<TeamMember> team = new ArrayList<>();

    @Column(nullable = false)
    private boolean published;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** day: 1 = Monday … 7 = Sunday (ISO); open/close as HH:MM. */
    public record DayHours(int day, String open, String close) {
    }

    public record TeamMember(String name, String role) {
    }
}
