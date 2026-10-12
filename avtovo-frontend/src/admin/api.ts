import api from '../api/client';
import type { CarListing, Page } from '../types';

// admin-service (gateway /api/admin/**) — ADMIN only

/** One admin.audit event (admin-service AuditController.AuditEntryView). */
export interface AuditEntry {
    id: string;
    occurredAt: string;
    actorId: string;
    actorEmail: string | null;
    service: string;
    action: string;
    targetType: string | null;
    targetId: string | null;
    reason: string | null;
    details: Record<string, unknown>;
}

export interface AuditFilters {
    actorId?: string;
    action?: string;
    targetType?: string;
    targetId?: string;
    service?: string;
    /** ISO instants */
    from?: string;
    to?: string;
    page?: number;
    size?: number;
}

/** admin-service SystemHealthService.InstanceHealth / SystemHealth. */
export interface InstanceHealth {
    service: string;
    instanceId: string | null;
    status: string;
    latencyMs: number | null;
    error: string | null;
}

export interface SystemHealth {
    checkedAt: string;
    overall: 'UP' | 'DEGRADED';
    instances: InstanceHealth[];
}

/** Drops empty values so they don't reach the backend as blank filters. */
export function cleanParams(params: object): Record<string, string | number | boolean> {
    return Object.fromEntries(Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')) as Record<string, string | number | boolean>;
}

// auth-service /api/auth/admin/** (AdminUserDtos)

export type Role = 'USER' | 'DEALER' | 'ADMIN';

export interface AdminUser {
    id: string;
    email: string;
    displayName: string | null;
    avatarUrl: string | null;
    role: Role;
    active: boolean;
    providers: string[];
    createdAt: string;
    lastLoginAt: string | null;
}

export interface DealerRequest {
    id: string;
    userId: string;
    email: string | null;
    displayName: string | null;
    state: 'pending' | 'confirmed' | 'expired';
    createdAt: string;
    expiresAt: string;
    confirmedAt: string | null;
}

export interface DailyCount {
    date: string;
    count: number;
}

export interface UserStats {
    total: number;
    byRole: Record<Role, number>;
    blocked: number;
    newLast7Days: number;
    newLast30Days: number;
    daily: DailyCount[];
    pendingDealerRequests: number;
}

/** user-service /api/users/admin/users/{id} (full UserProfileResponse; every read is audited). */
export interface AdminUserProfile {
    id: string;
    email: string;
    displayName?: string;
    phoneNumber?: string;
    city?: string;
    street?: string;
    houseNumber?: string;
    country?: string;
    bio?: string;
    avatarUrl?: string;
    role?: Role;
    active?: boolean;
    listingsCount?: number;
    createdAt?: string;
}

export interface UserFilters {
    q?: string;
    role?: string;
    active?: string;
    page?: number;
    size?: number;
    sort?: string;
}

export const listUsers = (filters: UserFilters) =>
    api.get<Page<AdminUser>>('/api/auth/admin/users', { params: cleanParams(filters) }).then(r => r.data);

export const getAdminUser = (id: string) =>
    api.get<AdminUser>(`/api/auth/admin/users/${id}`).then(r => r.data);

export const changeUserRole = (id: string, role: Role, reason: string) =>
    api.patch<AdminUser>(`/api/auth/admin/users/${id}/role`, { role, reason }).then(r => r.data);

export const blockUser = (id: string, reason: string) =>
    api.post<AdminUser>(`/api/auth/admin/users/${id}/block`, { reason }).then(r => r.data);

export const unblockUser = (id: string, reason: string) =>
    api.post<AdminUser>(`/api/auth/admin/users/${id}/unblock`, { reason }).then(r => r.data);

export const logoutUserEverywhere = (id: string, reason: string) =>
    api.post<void>(`/api/auth/admin/users/${id}/logout-all`, { reason }).then(() => undefined);

export const listDealerRequests = (state: string, page = 0) =>
    api.get<Page<DealerRequest>>('/api/auth/admin/dealer-requests', { params: { state, page, size: 20 } }).then(r => r.data);

export const getUserStats = () =>
    api.get<UserStats>('/api/auth/admin/stats').then(r => r.data);

export const getAdminUserProfile = (id: string) =>
    api.get<AdminUserProfile>(`/api/users/admin/users/${id}`).then(r => r.data);

// car-service /api/cars/admin/** (AdminListingDtos, ReportDtos)

export interface AdminListingRow {
    id: string;
    sellerId: string;
    make: string;
    model: string;
    year: number;
    price: number;
    city: string | null;
    status: string;
    primaryImageUrl: string | null;
    createdAt: string;
    promotedUntil: string | null;
    highlightedUntil: string | null;
    moderationReason: string | null;
    openReports: number;
}

export interface ReportView {
    id: string;
    carId: string;
    reporterId: string;
    reason: string;
    comment: string | null;
    status: 'OPEN' | 'RESOLVED' | 'DISMISSED';
    createdAt: string;
    resolvedBy: string | null;
    resolvedAt: string | null;
    resolutionNote: string | null;
}

export interface AdminListingDetail {
    listing: CarListing;
    moderatedBy: string | null;
    moderatedAt: string | null;
    reports: ReportView[];
}

export interface ReportGroup {
    carId: string;
    title: string | null;
    listingStatus: string | null;
    sellerId: string | null;
    primaryImageUrl: string | null;
    count: number;
    reasons: string[];
    latestAt: string;
}

export interface ListingStats {
    byStatus: Record<string, number>;
    newLast7Days: number;
    newLast30Days: number;
    daily: DailyCount[];
    openReports: number;
    listingsWithOpenReports: number;
    activePromotions: number;
}

export interface ListingFilters {
    status?: string;
    sellerId?: string;
    q?: string;
    from?: string;
    to?: string;
    promoted?: string;
    page?: number;
    size?: number;
    sort?: string;
}

export const listAdminListings = (filters: ListingFilters) =>
    api.get<Page<AdminListingRow>>('/api/cars/admin/listings', { params: cleanParams(filters) }).then(r => r.data);

export const getAdminListing = (id: string) =>
    api.get<AdminListingDetail>(`/api/cars/admin/listings/${id}`).then(r => r.data);

export const blockListing = (id: string, reason: string) =>
    api.post<AdminListingDetail>(`/api/cars/admin/listings/${id}/block`, { reason }).then(r => r.data);

export const unblockListing = (id: string, reason: string) =>
    api.post<AdminListingDetail>(`/api/cars/admin/listings/${id}/unblock`, { reason }).then(r => r.data);

export const removeListingImage = (id: string, url: string, reason: string) =>
    api.delete<AdminListingDetail>(`/api/cars/admin/listings/${id}/images`, { params: { url }, data: { reason } }).then(r => r.data);

export const listReportGroups = (status: string, page = 0) =>
    api.get<Page<ReportGroup>>('/api/cars/admin/reports', { params: { status, page, size: 20 } }).then(r => r.data);

export const dismissReports = (carId: string, note: string) =>
    api.post<{ dismissed: number }>(`/api/cars/admin/reports/listing/${carId}/dismiss`, { note }).then(r => r.data);

export const getListingStats = () =>
    api.get<ListingStats>('/api/cars/admin/stats').then(r => r.data);

export const getAuditLog = (filters: AuditFilters) =>
    api.get<Page<AuditEntry>>('/api/admin/audit', { params: cleanParams(filters) }).then(r => r.data);

export const getSystemHealth = () =>
    api.get<SystemHealth>('/api/admin/system/health').then(r => r.data);
