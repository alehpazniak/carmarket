import type { DocumentCategory, ImportOrderStatus, LotSource, PhotoStage } from '../types/importOrders';

export const STATUS_LABELS: Record<ImportOrderStatus, string> = {
    WON: 'Wygrany na aukcji',
    AUCTION_PAID: 'Opłacony na aukcji',
    PICKED_UP: 'Odebrany z placu aukcyjnego',
    AT_US_WAREHOUSE: 'W magazynie eksportowym (USA)',
    LOADED: 'Załadowany do kontenera',
    IN_TRANSIT: 'W drodze (statek)',
    ARRIVED_EU_PORT: 'W porcie docelowym',
    CUSTOMS_CLEARED: 'Odprawiony celnie',
    IN_TRANSIT_PL: 'Transport do Polski',
    AT_DEALER: 'Na placu dealera',
    HANDED_OVER: 'Wydany klientowi',
    ON_HOLD: 'Wstrzymane',
    CANCELLED: 'Anulowane',
};

/** The usual order of the shipping steps (ON_HOLD / CANCELLED are outside it). */
export const MAIN_STATUSES: ImportOrderStatus[] = [
    'WON', 'AUCTION_PAID', 'PICKED_UP', 'AT_US_WAREHOUSE', 'LOADED', 'IN_TRANSIT',
    'ARRIVED_EU_PORT', 'CUSTOMS_CLEARED', 'IN_TRANSIT_PL', 'AT_DEALER', 'HANDED_OVER',
];

export const ALL_STATUSES: ImportOrderStatus[] = [...MAIN_STATUSES, 'ON_HOLD', 'CANCELLED'];

/** The step after the current one, or null at the end / outside the main sequence. */
export function nextStatus(current: ImportOrderStatus | null): ImportOrderStatus | null {
    if (!current) return 'WON';
    const i = MAIN_STATUSES.indexOf(current);
    return i >= 0 && i < MAIN_STATUSES.length - 1 ? MAIN_STATUSES[i + 1] : null;
}

export function statusBadgeClass(status: ImportOrderStatus | null): string {
    if (!status) return 'bg-avtovo-border text-avtovo-text-secondary';
    if (status === 'CANCELLED') return 'bg-red-500/15 text-red-400';
    if (status === 'ON_HOLD') return 'bg-amber-500/15 text-amber-400';
    if (status === 'HANDED_OVER') return 'bg-emerald-500/15 text-emerald-400';
    return 'bg-avtovo-accent/15 text-avtovo-accent';
}

export const SOURCE_LABELS: Record<LotSource, string> = {
    COPART: 'Copart',
    IAAI: 'IAAI',
    OTHER: 'Inne',
};

export const DOCUMENT_CATEGORY_LABELS: Record<DocumentCategory, string> = {
    INVOICE_DEALER: 'Faktura',
    INVOICE_AUCTION: 'Faktura aukcyjna',
    TITLE: 'Dokument własności (title)',
    BILL_OF_LADING: 'Konosament',
    CUSTOMS: 'Dokumenty celne',
    TRANSPORT: 'Transport',
    PAYMENT_CONFIRMATION: 'Potwierdzenie płatności',
    OTHER: 'Inne',
};

export const PHOTO_STAGE_LABELS: Record<PhotoStage, string> = {
    AUCTION: 'Z aukcji',
    US_WAREHOUSE: 'Magazyn w USA',
    LOADING: 'Załadunek',
    ARRIVAL: 'Przypłynięcie',
    DELIVERY: 'Wydanie',
    OTHER: 'Inne',
};

export const DOCUMENT_CATEGORIES = Object.keys(DOCUMENT_CATEGORY_LABELS) as DocumentCategory[];
export const PHOTO_STAGES = Object.keys(PHOTO_STAGE_LABELS) as PhotoStage[];

export function categoryLabel(category: string): string {
    return (DOCUMENT_CATEGORY_LABELS as Record<string, string>)[category]
        ?? (PHOTO_STAGE_LABELS as Record<string, string>)[category]
        ?? category;
}

/** Same limits as import-order-service. */
export const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024;
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024;
export const MAX_PHOTOS_PER_UPLOAD = 30;
export const MAX_DOCUMENTS_PER_UPLOAD = 10;
export const MAX_AUCTION_PHOTOS = 20;

export function carTitle(o: { make?: string | null; model?: string | null; year?: number | null }): string {
    return [o.make, o.model, o.year].filter(Boolean).join(' ') || 'Pojazd bez nazwy';
}

export function formatDate(value: string | null | undefined): string {
    if (!value) return '—';
    const d = new Date(value.length === 10 ? `${value}T12:00:00` : value);
    return Number.isNaN(d.getTime()) ? value : d.toLocaleDateString('pl-PL');
}

export function formatDateTime(value: string | null | undefined): string {
    if (!value) return '—';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? value : d.toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
