export interface CarListing {
    id: string;
    sellerId: string;
    make: string;
    model: string;
    year: number;
    price: number;
    mileage: number;
    fuelType: 'PETROL' | 'DIESEL' | 'ELECTRIC' | 'HYBRID' | 'LPG';
    transmission: 'MANUAL' | 'AUTOMATIC';
    color: string;
    city: string;
    country: string;
    description?: string;
    imageUrls?: string[];
    primaryImageUrl?: string;
    equipment?: string[];
    status: 'ACTIVE' | 'SOLD' | 'REMOVED';
    createdAt: string;
}

export interface User {
    id: string;
    email: string;
    name: string;
    picture?: string;
}

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

export interface CarDocument {
    id: string;
    sellerId: string;
    make: string;
    model: string;
    year: number;
    price: number;
    mileage: number;
    fuelType: string;
    transmission: string;
    color?: string;
    description?: string;
    city: string;
    country?: string;
    status: string;
    createdAt: string;
    imageUrls?: string[];
    primaryImageUrl?: string;
}
/** Profile as returned by user-service (GET /api/users/me). */
export interface UserProfile {
    id: string;
    email: string;
    displayName?: string;
    phoneNumber?: string;
    city?: string;
    street?: string;
    houseNumber?: string;
}

export interface ContactInfo {
    phoneNumber: string;
    city: string;
    street: string;
    houseNumber: string;
}

/** payment-service (Przelewy24). Amounts are in grosz: 1999 = 19.99 PLN. */
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface PaymentProduct {
    code: string;
    amount: number;
    currency: string;
    description: string;
}

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

export interface CreatePaymentResponse {
    paymentId: string;
    status: PaymentStatus;
    redirectUrl: string;
}
