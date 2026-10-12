package com.carmarket.auth.dto;

import com.carmarket.auth.entity.DealerUpgradeRequest;
import com.carmarket.auth.entity.OAuthProvider;
import com.carmarket.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /auth/admin/** request and response bodies. */
public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** Destructive admin actions need a reason of at least 5 characters (it goes to the audit log). */
    public record ReasonRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
    }

    public record RoleChangeRequest(@NotNull User.UserRole role, @NotBlank @Size(min = 5, max = 500) String reason) {
    }

    public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String role,
        boolean active,
        List<String> providers,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
    ) {
        public static AdminUserView from(User u) {
            return new AdminUserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getRole().name(),
                u.isActive(), u.getProviders().stream().map(OAuthProvider::getProvider).map(Enum::name).sorted().toList(),
                u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record DealerRequestView(
        UUID id,
        UUID userId,
        String email,
        String displayName,
        String state,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt
    ) {
        public static DealerRequestView from(DealerUpgradeRequest r, User user, LocalDateTime now) {
            String state = r.getConfirmedAt() != null ? "confirmed" : r.getExpiresAt().isAfter(now) ? "pending" : "expired";
            return new DealerRequestView(r.getId(), r.getUserId(), user != null ? user.getEmail() : null,
                user != null ? user.getDisplayName() : null, state, r.getCreatedAt(), r.getExpiresAt(), r.getConfirmedAt());
        }
    }

    public record DailyCount(LocalDate date, long count) {
    }

    public record UserStats(
        long total,
        Map<String, Long> byRole,
        long blocked,
        long newLast7Days,
        long newLast30Days,
        List<DailyCount> daily,
        long pendingDealerRequests
    ) {
    }
}
