import axios from 'axios';
import api from './client';
import type { Page } from '../types';
import type {
    AccessLogEntry,
    ClientCredentials,
    ClientOrderView,
    CreateImportOrderRequest,
    CreatedImportOrder,
    FileKind,
    ImportOrderDetail,
    ImportOrderFields,
    ImportOrderFile,
    ImportOrderStatus,
    ImportOrderSummary,
    PresignedUrl,
} from '../types/importOrders';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// ── Dealer cabinet (logged-in DEALER / ADMIN, shared client with token refresh) ──

export interface ImportOrderListParams {
    status?: ImportOrderStatus | '';
    q?: string;
    archived?: boolean;
    /** Admins only: one dealer's orders (ignored for dealers). */
    dealerId?: string;
    page?: number;
    size?: number;
}

export const listImportOrders = (params: ImportOrderListParams = {}) =>
    api.get<Page<ImportOrderSummary>>('/api/import-orders', {
        params: { ...params, status: params.status || undefined, q: params.q || undefined },
    }).then(r => r.data);

export const createImportOrder = (body: CreateImportOrderRequest) =>
    api.post<CreatedImportOrder>('/api/import-orders', body).then(r => r.data);

export const getImportOrder = (id: string) =>
    api.get<ImportOrderDetail>(`/api/import-orders/${id}`).then(r => r.data);

export const updateImportOrder = (id: string, fields: ImportOrderFields) =>
    api.patch<ImportOrderDetail>(`/api/import-orders/${id}`, fields).then(r => r.data);

export const deleteImportOrder = (id: string) =>
    api.delete(`/api/import-orders/${id}`);

export const setImportOrderArchived = (id: string, archived: boolean) =>
    api.post<ImportOrderDetail>(`/api/import-orders/${id}/${archived ? 'archive' : 'unarchive'}`).then(r => r.data);

export const addImportOrderEvent = (id: string, event: {
    status: ImportOrderStatus;
    occurredAt: string;
    clientNote?: string;
    internalNote?: string;
}) => api.post<ImportOrderDetail>(`/api/import-orders/${id}/events`, event).then(r => r.data);

export const deleteImportOrderEvent = (id: string, eventId: string) =>
    api.delete<ImportOrderDetail>(`/api/import-orders/${id}/events/${eventId}`).then(r => r.data);

export const uploadImportOrderFiles = (id: string, files: (File | Blob)[], kind: FileKind, category: string,
                                       clientVisible: boolean, names?: string[]) => {
    const form = new FormData();
    files.forEach((f, i) => form.append('files', f, names?.[i] ?? (f instanceof File ? f.name : `plik-${i + 1}`)));
    form.append('kind', kind);
    form.append('category', category);
    form.append('clientVisible', String(clientVisible));
    return api.post<ImportOrderFile[]>(`/api/import-orders/${id}/files`, form, {
        // Let the browser set multipart/form-data with its boundary
        headers: { 'Content-Type': undefined },
    }).then(r => r.data);
};

export const updateImportOrderFile = (id: string, fileId: string, patch: { clientVisible?: boolean; category?: string }) =>
    api.patch<ImportOrderFile>(`/api/import-orders/${id}/files/${fileId}`, patch).then(r => r.data);

export const deleteImportOrderFile = (id: string, fileId: string) =>
    api.delete(`/api/import-orders/${id}/files/${fileId}`);

export const getImportOrderFileUrl = (id: string, fileId: string) =>
    api.get<PresignedUrl>(`/api/import-orders/${id}/files/${fileId}/url`).then(r => r.data);

export const resetClientAccess = (id: string, rotateLink = false) =>
    api.post<ClientCredentials>(`/api/import-orders/${id}/access/reset`, { rotateLink }).then(r => r.data);

export const setClientAccessEnabled = (id: string, enabled: boolean) =>
    api.patch<ImportOrderDetail>(`/api/import-orders/${id}/access`, { enabled }).then(r => r.data);

export const getAccessLog = (id: string) =>
    api.get<AccessLogEntry[]>(`/api/import-orders/${id}/access-log`).then(r => r.data);

export const getClientPreview = (id: string) =>
    api.get<ClientOrderView>(`/api/import-orders/${id}/client-preview`).then(r => r.data);

/** An auction photo fetched through the logged-in image proxy (the auction sites block hotlinking). */
export const fetchAuctionImage = (url: string) =>
    api.get<Blob>('/api/auctions/apibara/image-proxy', { params: { url }, responseType: 'blob' }).then(r => r.data);

/** Copart / IAAI lot page URL → Apibara identifiers (spends one Apibara request). */
export const resolveAuctionUrl = (url: string) =>
    api.get<Record<string, unknown>>('/api/auctions/apibara/vehicles/url-to-details', { params: { url } }).then(r => r.data);

// ── Client page (/zlecenie/:code) ──
// A separate axios instance on purpose: no Authorization header and no refresh interceptor. The
// shared client clears localStorage and redirects to "/" on a 401, which would break this page and
// log out a dealer who opens it to check what the client sees.

const clientPageApi = axios.create({
    baseURL: API_URL,
    headers: { 'Content-Type': 'application/json' },
});

export const clientOrderAccess = (code: string, password: string) =>
    clientPageApi.post<{ token: string; expiresAt: string }>(
        `/api/import-orders/public/${encodeURIComponent(code)}/access`, { password }).then(r => r.data);

export const getClientOrder = (code: string, token: string) =>
    clientPageApi.get<ClientOrderView>(`/api/import-orders/public/${encodeURIComponent(code)}`, {
        headers: { 'X-Order-Token': token },
    }).then(r => r.data);
