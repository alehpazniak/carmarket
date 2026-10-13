import api from '../api/client';
import type { Page } from '../types';

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

export const getAuditLog = (filters: AuditFilters) =>
    api.get<Page<AuditEntry>>('/api/admin/audit', { params: cleanParams(filters) }).then(r => r.data);

export const getSystemHealth = () =>
    api.get<SystemHealth>('/api/admin/system/health').then(r => r.data);
