export type VehicleCategory =
    'PASSENGER' | 'TRUCK' | 'CONSTRUCTION' | 'VAN' | 'MOTORCYCLE' | 'TRAILER' | 'AGRICULTURAL';

/** GET /api/cars, /api/cars/{id} — car-service's CarListingResponse (camelCase). */
export interface CarListing {
    id: string;
    sellerId: string;
    make: string;
    model: string;
    year: number;
    price: number;
    mileage: number;
    category?: VehicleCategory;
    fuelType: 'PETROL' | 'DIESEL' | 'ELECTRIC' | 'HYBRID' | 'LPG';
    transmission: 'MANUAL' | 'AUTOMATIC' | 'SEMI_AUTOMATIC';
    color: string;
    city: string;
    country: string;
    description?: string;
    imageUrls?: string[];
    primaryImageUrl?: string;
    equipment?: string[];
    /** Canonical YouTube, TikTok or Instagram link (car-service normalizes whatever the seller pasted). */
    videoUrl?: string | null;
    technicalData?: TechnicalData | null;
    /** SUSPENDED: the owner's account is blocked; BLOCKED: hidden by a moderator (only the owner and admins see them). */
    status: 'ACTIVE' | 'SOLD' | 'DRAFT' | 'REMOVED' | 'SUSPENDED' | 'BLOCKED';
    /** Why a moderator blocked the listing; only in /api/cars/my/{id} and admin views. */
    moderationReason?: string | null;
    /** End of a paid promotion / highlight (car-service LocalDateTime, no zone); null when none. */
    promotedUntil?: string | null;
    highlightedUntil?: string | null;
    createdAt: string;
}

/** GET /api/cars/summaries (car-service's CarListingSummary): enough to label a listing anywhere, REMOVED ones included. */
export interface CarSummary {
    id: string;
    make: string;
    model: string;
    year: number;
    price: number;
    primaryImageUrl?: string | null;
    status: 'ACTIVE' | 'SOLD' | 'DRAFT' | 'REMOVED' | 'SUSPENDED' | 'BLOCKED';
}

export type DriveType = 'FRONT' | 'REAR' | 'AWD_AUTO' | 'AWD_MANUAL' | 'AWD_PERMANENT';
export type BodyType = 'MINI' | 'CITY' | 'COUPE' | 'CABRIO' | 'ESTATE' | 'COMPACT' | 'MINIVAN' | 'SEDAN' | 'SUV';
export type VehicleCondition = 'NEW' | 'USED';

/** "Dane techniczne" block (car-service's TechnicalDataDto); every field is optional. */
export interface TechnicalData {
    version?: string | null;
    engineCapacity?: number | null;
    powerHp?: number | null;
    driveType?: DriveType | null;
    bodyType?: BodyType | null;
    doors?: number | null;
    seats?: number | null;
    vin?: string | null;
    condition?: VehicleCondition | null;
    originCountry?: string | null;
    /** ISO date, yyyy-MM-dd */
    firstRegistrationDate?: string | null;
    registeredInPoland?: boolean | null;
    firstOwner?: boolean | null;
    accidentFree?: boolean | null;
    asoServiced?: boolean | null;
    damaged?: boolean | null;
}

export interface User {
    id: string;
    email: string;
    name: string;
    picture?: string;
}

/** POST /api/auth/refresh — auth-service's AuthResponse (snake_case @JsonProperty names). */
export interface AuthTokens {
    access_token: string;
    refresh_token: string;
    user_id: string;
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

/** GET /api/search — search-service's CarDocument (camelCase); it has no imageUrls, only primaryImageUrl. */
export interface CarDocument {
    id: string;
    sellerId: string;
    make: string;
    model: string;
    year: number;
    price: number;
    mileage: number;
    category?: string;
    fuelType: string;
    transmission: string;
    color?: string;
    description?: string;
    city: string;
    country?: string;
    status: string;
    createdAt: string;
    primaryImageUrl?: string;
    promotedUntil?: string | null;
    highlightedUntil?: string | null;
    /** Computed by search-service at indexing time; the source of truth for search results. */
    promoted?: boolean;
    highlighted?: boolean;
}
/** Another user's profile (GET /api/users/{id}, user-service's PublicUserProfileResponse): an allow-list, no email, phone or address. */
export interface PublicUserProfile {
    id: string;
    displayName?: string;
    avatarUrl?: string;
    city?: string;
    bio?: string;
    role?: UserRole;
    memberSince?: string;
    /** Set when the user has a published dealer page (/dealer/{slug}). */
    dealerSlug?: string;
}

/** The logged-in user's own full profile (GET /api/users/me only, user-service's UserProfileResponse). */
export interface UserProfile {
    id: string;
    email: string;
    displayName?: string;
    phoneNumber?: string;
    city?: string;
    street?: string;
    houseNumber?: string;
    role?: UserRole;
}

export type UserRole = 'USER' | 'DEALER' | 'ADMIN';

/** PUT /api/users/me/contact — user-service's ContactInfoRequest. */
export interface ContactInfo {
    phoneNumber: string;
    city: string;
    street: string;
    houseNumber: string;
}

/** payment-service (Przelewy24). Amounts are in grosz: 1999 = 19.99 PLN. */
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

/** GET /api/payments/products — payment-service's PaymentDtos.ProductResponse. */
export interface PaymentProduct {
    code: string;
    amount: number;
    currency: string;
    description: string;
    /** How long the feature lasts; absent for products without a duration. */
    durationDays?: number;
}

/** payment-service's PaymentDtos.PaymentResponse. */
export interface Payment {
    id: string;
    productCode: string;
    referenceId?: string;
    amount: number;
    currency: string;
    description: string;
    status: PaymentStatus;
    createdAt: string;
    paidAt?: string;
    refundedAt?: string;
}

/** POST /api/payments — payment-service's PaymentDtos.CreatePaymentResponse. */
export interface CreatePaymentResponse {
    paymentId: string;
    status: PaymentStatus;
    redirectUrl: string;
}
