package com.carmarket.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores OAuth2 provider identity per user.
 * Supports multiple providers for the same user (Google + Facebook linked).
 */
@Entity
@Table(name = "oauth_providers", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;  // Google sub / Facebook id

    @Column(name = "access_token")
    private String accessToken;     // OAuth2 provider access token (for API calls)

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    public enum ProviderType {
        GOOGLE, FACEBOOK
    }
}
