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
}