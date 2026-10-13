import type { Role } from './api';

export const ROLE_LABELS: Record<Role, string> = { ADMIN: 'Administrator', DEALER: 'Dealer', USER: 'Użytkownik' };

/** Polish names of admin.audit actions; unknown ones are shown as sent. */
export const ACTION_LABELS: Record<string, string> = {
    USER_ROLE_CHANGED: 'Zmiana roli',
    USER_BLOCKED: 'Blokada konta',
    USER_UNBLOCKED: 'Odblokowanie konta',
    USER_LOGGED_OUT: 'Wylogowanie ze wszystkich urządzeń',
    USER_PII_VIEWED: 'Podgląd danych osobowych',
    LISTING_BLOCKED: 'Blokada ogłoszenia',
    LISTING_UNBLOCKED: 'Odblokowanie ogłoszenia',
    LISTING_IMAGE_REMOVED: 'Usunięcie zdjęcia',
    REPORTS_DISMISSED: 'Odrzucenie zgłoszeń',
    SEARCH_REINDEXED: 'Przeindeksowanie wyszukiwarki',
    PAYMENT_REFUNDED: 'Zwrot płatności',
    AUCTION_SYNC_TRIGGERED: 'Synchronizacja aukcji',
    NOTIFICATION_RETRIED: 'Ponowienie powiadomienia',
};

export const actionLabel = (action: string) => ACTION_LABELS[action] ?? action;

export const TARGET_LABELS: Record<string, string> = {
    USER: 'Użytkownik',
    LISTING: 'Ogłoszenie',
    PAYMENT: 'Płatność',
    NOTIFICATION: 'Powiadomienie',
    SEARCH_INDEX: 'Indeks wyszukiwarki',
    AUCTION_SYNC: 'Synchronizacja aukcji',
};

export const SERVICES = [
    'auth-service', 'user-service', 'car-service', 'chat-service', 'payment-service',
    'notification-service', 'auction-import-service', 'import-order-service', 'admin-service',
];

export const formatDateTime = (iso: string | null | undefined) =>
    iso ? new Date(iso).toLocaleString('pl-PL', { dateStyle: 'short', timeStyle: 'short' }) : '—';
