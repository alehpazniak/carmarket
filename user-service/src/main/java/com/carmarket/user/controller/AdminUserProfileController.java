package com.carmarket.user.controller;

import com.carmarket.user.audit.AdminAuditPublisher;
import com.carmarket.user.dto.UserProfileResponse;
import com.carmarket.user.exception.UserProfileNotFoundException;
import com.carmarket.user.mapper.UserProfileMapper;
import com.carmarket.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Gateway: /api/users/admin/** — ADMIN only. The full profile (email, phone, address), also for
 * deactivated accounts; every read is audited as USER_PII_VIEWED.
 */
@RestController
@RequestMapping("/users/admin/users")
@RequiredArgsConstructor
public class AdminUserProfileController {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;
    private final AdminAuditPublisher auditPublisher;

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public UserProfileResponse profile(@PathVariable UUID id,
                                       @AuthenticationPrincipal String adminId,
                                       @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        UserProfileResponse response = repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new UserProfileNotFoundException(id.toString()));
        auditPublisher.publish(new AdminAuditPublisher.Actor(adminId, adminEmail), "USER_PII_VIEWED", "USER",
            id.toString(), null, Map.of());
        return response;
    }
}
