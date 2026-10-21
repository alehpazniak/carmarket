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

// payment-service /api/payments/admin/** (AdminPaymentDtos). Amounts in grosz.

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface AdminPayment {
    id: string;
    userId: string;
    email: string;
    productCode: string;
    referenceId: string | null;
    amount: number;
    currency: string;
    description: string;
    status: PaymentStatus;
    p24OrderId: number | null;
    p24MethodId: number | null;
    failureReason: string | null;
    createdAt: string;
    paidAt: string | null;
    refundedAt: string | null;
}

export interface PaymentFilters {
    status?: string;
    productCode?: string;
    userId?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
}

export interface PaymentStats {
    from: string;
    to: string;
    revenue: number;
    revenueByProduct: { productCode: string; amount: number; count: number }[];
    countByStatus: Record<PaymentStatus, number>;
    refundedAmount: number;
    refundedCount: number;
    daily: { date: string; amount: number; count: number }[];
}

export const listAdminPayments = (filters: PaymentFilters) =>
    api.get<Page<AdminPayment>>('/api/payments/admin/payments', { params: cleanParams(filters) }).then(r => r.data);

export const getAdminPayment = (id: string) =>
    api.get<AdminPayment>(`/api/payments/admin/payments/${id}`).then(r => r.data);

export const refundPayment = (id: string, reason: string) =>
    api.post(`/api/payments/${id}/refund`, { reason }).then(r => r.data);

/** Downloads the CSV (max 10,000 rows) through the authenticated client; returns whether it was cut off. */
export async function downloadPaymentsCsv(filters: PaymentFilters): Promise<boolean> {
    const res = await api.get<Blob>('/api/payments/admin/payments.csv', { params: cleanParams(filters), responseType: 'blob' });
    const url = URL.createObjectURL(res.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = `platnosci-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    return res.headers['x-export-truncated'] === 'true';
}

export const getPaymentStats = (from?: string, to?: string) =>
    api.get<PaymentStats>('/api/payments/admin/stats', { params: cleanParams({ from, to }) }).then(r => r.data);

// import-order-service

export interface ImportOrderStats {
    total: number;
    open: number;
    archived: number;
    byStatus: Record<string, number>;
    newLast30Days: number;
    dealers: number;
    daily: DailyCount[];
}

export const getImportOrderStats = () =>
    api.get<ImportOrderStats>('/api/import-orders/admin/stats').then(r => r.data);

// auction-import-service /api/auctions/admin/**

export interface SyncRun {
    id: string;
    parser: string;
    trigger: 'SCHEDULED' | 'MANUAL';
    startedAt: string;
    finishedAt: string | null;
    lotsFetched: number | null;
    lotsAdded: number | null;
    lotsUpdated: number | null;
    error: string | null;
    /** Requests the run sent to Apibara (null for runs before V7). */
    apiRequests: number | null;
    quotaRemaining: number | null;
}

/** The quota headers of the sync's latest Apibara answer; any field may be null. */
export interface ApibaraQuota {
    limit: number | null;
    used: number | null;
    remaining: number | null;
    periodStart: string | null;
    periodEnd: string | null;
    rateLimitRemaining: number | null;
    perPageLimit: number | null;
    observedAt: string;
}

export interface AuctionStatus {
    apibaraConfigured: boolean;
    apibaraQuota: ApibaraQuota | null;
    /** GET /usage, only filled with refresh=true (it may spend a request of the quota). */
    apibaraUsage: Record<string, unknown> | null;
    apibaraUsageError: string | null;
    syncRunning: boolean;
    lastRuns: SyncRun[];
    exchangeRate: { rate: number; fetchedAt: string; fallback: boolean } | null;
    /** import.w8.price-list and its row count in w8_towing_rate (0 = the price list and the table don't match). */
    w8PriceList: string;
    w8TowingRows: number;
}

export const getAuctionStatus = (refreshUsage = false) =>
    api.get<AuctionStatus>('/api/auctions/admin/status', { params: refreshUsage ? { refresh: true } : undefined })
        .then(r => r.data);

export const triggerAuctionSync = () =>
    api.post('/api/auctions/admin/sync').then(r => r.data);

/** car-service: re-publishes every listing so search-service rebuilds the index. */
export const reindexSearch = () =>
    api.post<{ status: string; published: number }>('/api/cars/admin/reindex').then(r => r.data);

// notification-service /api/notifications/admin/**

export type NotificationStatus = 'PENDING' | 'SENT' | 'CANCELLED' | 'ACKNOWLEDGED' | 'FAILED';

export interface AdminNotification {
    id: string;
    type: string;
    status: NotificationStatus;
    recipientId: string;
    recipientEmail: string | null;
    createdAt: string;
    sendAt: string;
    sentAt: string | null;
    attempts: number;
    lastError: string | null;
}

export interface NotificationStats {
    byStatus: Record<NotificationStatus, number>;
    failedLast24h: number;
    sentLast7Days: number;
    failedByType: Record<string, number>;
    pending: number;
}

export const listNotifications = (status: string, type: string, page = 0) =>
    api.get<Page<AdminNotification>>('/api/notifications/admin/notifications',
        { params: cleanParams({ status, type, page, size: 20 }) }).then(r => r.data);

export const retryNotification = (id: string) =>
    api.post(`/api/notifications/admin/notifications/${id}/retry`).then(() => undefined);

export const getNotificationStats = () =>
    api.get<NotificationStats>('/api/notifications/admin/stats').then(r => r.data);

// chat-service /api/chat/admin/stats — counts only

export interface ChatStats {
    conversations: number;
    newConversationsLast7Days: number;
    newConversationsLast30Days: number;
    messagesLast7Days: number;
    messagesLast30Days: number;
    dailyMessages: DailyCount[];
    blockedUsers: number;
}

export const getChatStats = () =>
    api.get<ChatStats>('/api/chat/admin/stats').then(r => r.data);

export const getAuditLog = (filters: AuditFilters) =>
    api.get<Page<AuditEntry>>('/api/admin/audit', { params: cleanParams(filters) }).then(r => r.data);

export const getSystemHealth = () =>
    api.get<SystemHealth>('/api/admin/system/health').then(r => r.data);
