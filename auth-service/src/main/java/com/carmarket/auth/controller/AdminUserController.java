package com.carmarket.auth.controller;

import com.carmarket.auth.audit.AdminAuditPublisher.Actor;
import com.carmarket.auth.dto.AdminUserDtos.AdminUserView;
import com.carmarket.auth.dto.AdminUserDtos.DealerRequestView;
import com.carmarket.auth.dto.AdminUserDtos.ReasonRequest;
import com.carmarket.auth.dto.AdminUserDtos.RoleChangeRequest;
import com.carmarket.auth.dto.AdminUserDtos.UserStats;
import com.carmarket.auth.entity.User;
import com.carmarket.auth.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/** Gateway: /api/auth/admin/** — ADMIN only (SecurityConfig), never public (JwtAuthenticationFilter). */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "lastLoginAt", "email", "displayName", "role");
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService service;

    @GetMapping("/users")
    public Page<AdminUserView> users(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) User.UserRole role,
                                     @RequestParam(required = false) Boolean active,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(q, role, active, pageRequest(page, size, sort));
    }

    @GetMapping("/users/{id}")
    public AdminUserView user(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserView changeRole(@PathVariable UUID id, @Valid @RequestBody RoleChangeRequest request,
                                    @AuthenticationPrincipal String adminId,
                                    @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.changeRole(id, request.role(), request.reason(), new Actor(adminId, adminEmail));
    }

    @PostMapping("/users/{id}/block")
    public AdminUserView block(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                               @AuthenticationPrincipal String adminId,
                               @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.block(id, request.reason(), new Actor(adminId, adminEmail));
    }

    @PostMapping("/users/{id}/unblock")
    public AdminUserView unblock(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                 @AuthenticationPrincipal String adminId,
                                 @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        return service.unblock(id, request.reason(), new Actor(adminId, adminEmail));
    }

    @PostMapping("/users/{id}/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                          @AuthenticationPrincipal String adminId,
                          @RequestHeader(value = "X-User-Email", required = false) String adminEmail) {
        service.logoutEverywhere(id, request.reason(), new Actor(adminId, adminEmail));
    }

    @GetMapping("/dealer-requests")
    public Page<DealerRequestView> dealerRequests(@RequestParam(defaultValue = "pending") String state,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.dealerRequests(state, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/stats")
    public UserStats stats() {
        return service.stats();
    }

    /** Only whitelisted fields: an arbitrary sort property would be a 500 instead of a 400. */
    static PageRequest pageRequest(int page, int size, String sort) {
        String[] parts = sort.split(",");
        if (!SORT_FIELDS.contains(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field: " + parts[0]);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(new Sort.Order(direction, parts[0]).nullsLast()));
    }
}
