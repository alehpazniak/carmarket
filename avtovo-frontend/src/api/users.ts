import axios from 'axios';
import api from './client';
import type {ContactInfo, PublicUserProfile, UserProfile} from '../types';

/** The logged-in user's own profile, including private address fields. */
export async function getMyProfile(): Promise<UserProfile> {
    const res = await api.get('/api/users/me');
    return res.data;
}

/** Replaces phone and address. Phone, city and street are required; a blank house number clears it. */
export async function updateContactInfo(info: ContactInfo): Promise<UserProfile> {
    const res = await api.put('/api/users/me/contact', info);
    return res.data;
}

/** Another user's public profile (allow-listed fields only). Requires login, like every /api/users call. */
export async function getUserProfile(userId: string): Promise<PublicUserProfile> {
    const res = await api.get(`/api/users/${userId}`);
    return res.data;
}

/**
 * The seller's phone number for one of their ACTIVE listings; null when there is none to show
 * (the backend answers 404 for every reason). Rate-limited per user — a 429 is rethrown.
 */
export async function getSellerPhone(sellerId: string, carId: string): Promise<string | null> {
    try {
        const res = await api.get<{ phoneNumber: string }>(`/api/users/${sellerId}/phone`, {params: {carId}});
        return res.data.phoneNumber?.trim() || null;
    } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) return null;
        throw err;
    }
}
