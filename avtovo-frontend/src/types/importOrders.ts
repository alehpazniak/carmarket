// import-order-service (dealer cabinet + client page /zlecenie/:code)

export type ImportOrderStatus =
    | 'WON' | 'AUCTION_PAID' | 'PICKED_UP' | 'AT_US_WAREHOUSE' | 'LOADED' | 'IN_TRANSIT'
    | 'ARRIVED_EU_PORT' | 'CUSTOMS_CLEARED' | 'IN_TRANSIT_PL' | 'AT_DEALER' | 'HANDED_OVER'
    | 'ON_HOLD' | 'CANCELLED';

export type LotSource = 'COPART' | 'IAAI' | 'OTHER';
export type FileKind = 'DOCUMENT' | 'PHOTO';
export type DocumentCategory =
    | 'INVOICE_DEALER' | 'INVOICE_AUCTION' | 'TITLE' | 'BILL_OF_LADING' | 'CUSTOMS' | 'TRANSPORT'
    | 'PAYMENT_CONFIRMATION' | 'CONTRACT' | 'OTHER';
export type PhotoStage = 'AUCTION' | 'US_WAREHOUSE' | 'LOADING' | 'ARRIVAL' | 'DELIVERY' | 'OTHER';
export type AccessEvent = 'LOGIN_OK' | 'LOGIN_FAILED' | 'VIEW';

/** Editable order fields (create + PATCH). Absent = unchanged, null = clear. */
export interface ImportOrderFields {
    source?: LotSource;
    lotNumber?: string | null;
    vin?: string | null;
    make?: string | null;
    model?: string | null;
    year?: number | null;
    /** yyyy-MM-dd */
    saleDate?: string | null;
    purchasePriceUsd?: number | null;
    auctionLocation?: string | null;
    /** The lot page on Copart/IAAI the dealer pasted; dealer-only, never on the client page. */
    auctionUrl?: string | null;
    titleType?: string | null;
    titleExportOk?: boolean | null;
    titleRegistrationOk?: boolean | null;
    paymentDeadline?: string | null;
    titleReceivedAt?: string | null;
    portOfLoading?: string | null;
    shippingLine?: string | null;
    containerNumber?: string | null;
    bookingNumber?: string | null;
    billOfLading?: string | null;
    vesselName?: string | null;
    etd?: string | null;
    eta?: string | null;
    destinationPort?: string | null;
    customsClearedAt?: string | null;
    clientName?: string | null;
    clientEmail?: string | null;
    clientPhone?: string | null;
    notifyClient?: boolean;
    dealerContactName?: string | null;
    dealerContactPhone?: string | null;
    dealerContactEmail?: string | null;
    internalNotes?: string | null;
}

export interface CreateImportOrderRequest extends ImportOrderFields {
    source: LotSource;
    /** Vehicle JSON from the auction page, stored as it was at creation time. */
    auctionSnapshot?: Record<string, unknown> | null;
}

export interface ImportOrderSummary {
    id: string;
    /** Owner of the order (admins see every dealer's orders). */
    dealerId: string;
    status: ImportOrderStatus | null;
    source: LotSource;
    lotNumber: string | null;
    vin: string | null;
    make: string | null;
    model: string | null;
    year: number | null;
    clientName: string | null;
    eta: string | null;
    containerNumber: string | null;
    auctionUrl: string | null;
    /** Presigned (5 min) URL of the order's first auction photo, else its first photo. */
    thumbnailUrl: string | null;
    /** First photo in the auction snapshot; load it through the image proxy (the auction sites block hotlinking). */
    auctionPhotoUrl: string | null;
    createdAt: string;
    updatedAt: string;
    archivedAt: string | null;
}

export interface ImportOrderEvent {
    id: string;
    status: ImportOrderStatus;
    occurredAt: string;
    clientNote: string | null;
    internalNote: string | null;
    createdBy: string;
    createdAt: string;
}

export interface ImportOrderFile {
    id: string;
    kind: FileKind;
    category: string;
    originalName: string;
    contentType: string;
    sizeBytes: number;
    clientVisible: boolean;
    createdAt: string;
}

export interface ImportOrderDetail extends Required<Omit<ImportOrderFields, 'notifyClient' | 'source'>> {
    id: string;
    dealerId: string;
    status: ImportOrderStatus | null;
    source: LotSource;
    notifyClient: boolean;
    auctionSnapshot: Record<string, unknown> | null;
    createdAt: string;
    updatedAt: string;
    archivedAt: string | null;
    access: {
        clientLink: string;
        enabled: boolean;
        accessVersion: number;
        failedAttempts: number;
        lockedUntil: string | null;
    };
    events: ImportOrderEvent[];
    files: ImportOrderFile[];
    warnings: string[];
    storageEnabled: boolean;
}

/** Shown once after create / reset: only the hash is stored. */
export interface ClientCredentials {
    clientLink: string;
    password: string;
}

export interface CreatedImportOrder extends ClientCredentials {
    order: ImportOrderDetail;
}

export interface PresignedUrl {
    url: string;
    expiresAt: string;
}

export interface AccessLogEntry {
    event: AccessEvent;
    at: string;
    ipPrefix: string | null;
    userAgent: string | null;
}

/** What the client sees — also returned by the dealer's client-preview. */
export interface ClientOrderView {
    source: LotSource;
    lotNumber: string | null;
    vin: string | null;
    make: string | null;
    model: string | null;
    year: number | null;
    saleDate: string | null;
    auctionLocation: string | null;
    titleType: string | null;
    titleExportOk: boolean | null;
    titleRegistrationOk: boolean | null;
    status: ImportOrderStatus | null;
    /** Newest first. */
    events: { status: ImportOrderStatus; occurredAt: string; note: string | null }[];
    shipping: {
        paymentDeadline: string | null;
        titleReceivedAt: string | null;
        portOfLoading: string | null;
        shippingLine: string | null;
        containerNumber: string | null;
        bookingNumber: string | null;
        billOfLading: string | null;
        vesselName: string | null;
        etd: string | null;
        eta: string | null;
        destinationPort: string | null;
        customsClearedAt: string | null;
    };
    documents: ClientOrderFile[];
    photos: ClientOrderFile[];
    dealer: { name: string | null; phone: string | null; email: string | null };
    urlsExpireAt: string | null;
}

export interface ClientOrderFile {
    id: string;
    kind: FileKind;
    category: string;
    originalName: string;
    contentType: string;
    sizeBytes: number;
    /** Short-lived presigned URL; null while storage is not configured. */
    url: string | null;
}
