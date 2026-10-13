/** car-service ReportReason. */
export type ReportReason = 'SCAM' | 'WRONG_DATA' | 'DUPLICATE' | 'OFFENSIVE' | 'ALREADY_SOLD' | 'OTHER';

export const REPORT_REASON_LABELS: Record<ReportReason, string> = {
    SCAM: 'Oszustwo lub podejrzana oferta',
    WRONG_DATA: 'Nieprawdziwe dane pojazdu',
    DUPLICATE: 'Duplikat ogłoszenia',
    OFFENSIVE: 'Obraźliwe treści lub zdjęcia',
    ALREADY_SOLD: 'Pojazd już sprzedany',
    OTHER: 'Inny powód',
};

export const LISTING_STATUS_LABELS: Record<string, string> = {
    ACTIVE: 'Aktywne',
    SOLD: 'Sprzedane',
    DRAFT: 'Szkic',
    REMOVED: 'Usunięte',
    SUSPENDED: 'Zawieszone',
    BLOCKED: 'Zablokowane',
};
