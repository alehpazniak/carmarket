import axios from 'axios';
import api from './client';
import type { CarListing, Page } from '../types';
import type { DealerCard, DealerMapItem, DealerPageOwner, DealerPageRequest, DealerPageView } from '../types/dealers';

/** A published dealer page; null when there is none (unknown slug, unpublished or blocked dealer). */
export async function getDealerPage(slug: string): Promise<DealerPageView | null> {
    try {
        return (await api.get<DealerPageView>(`/api/users/dealers/${encodeURIComponent(slug)}`)).data;
    } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) return null;
        throw err;
    }
}

/** "Wyświetl numer": rate-limited per IP at the gateway, so a 429 is rethrown. Null on 404. */
export async function getDealerPhone(slug: string): Promise<string | null> {
    try {
        const res = await api.get<{ phoneNumber: string }>(`/api/users/dealers/${encodeURIComponent(slug)}/phone`);
        return res.data.phoneNumber?.trim() || null;
    } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) return null;
        throw err;
    }
}

/** The seller's published dealer page, for the box on their listing; null for private sellers. */
export async function getDealerCard(userId: string): Promise<DealerCard | null> {
    try {
        return (await api.get<DealerCard>(`/api/users/dealers/by-user/${userId}`)).data;
    } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) return null;
        throw err;
    }
}

/** Dealer map: visible pages within radiusKm (1–300) of a point, nearest first. Rate-limited per IP (429). */
export const getNearbyDealers = (lat: number, lng: number, radiusKm: number, limit = 200, signal?: AbortSignal) =>
    api.get<DealerMapItem[]>('/api/users/dealers/nearby', { params: { lat, lng, radiusKm, limit }, signal })
        .then(r => r.data);

/** The dealer's ACTIVE listings ("Oferta"); sort is "createdAt,desc", "price,asc", ... */
export const getSellerListings = (sellerId: string, page: number, sort: string, size = 24) =>
    api.get<Page<CarListing>>(`/api/cars/seller/${sellerId}`, { params: { page, size, sort } }).then(r => r.data);

/** The logged-in dealer's own page; null if they haven't created one yet. */
export async function getMyDealerPage(): Promise<DealerPageOwner | null> {
    try {
        return (await api.get<DealerPageOwner>('/api/users/me/dealer-page')).data;
    } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) return null;
        throw err;
    }
}

export const saveMyDealerPage = (page: DealerPageRequest) =>
    api.put<DealerPageOwner>('/api/users/me/dealer-page', page).then(r => r.data);

/** Logo, banner or gallery image; returns its public URL to put into the page. */
export const uploadDealerImage = (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post<{ url: string }>('/api/cars/dealer-media', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data.url);
};

export const deleteDealerImage = (url: string) =>
    api.delete('/api/cars/dealer-media', { params: { url } });
