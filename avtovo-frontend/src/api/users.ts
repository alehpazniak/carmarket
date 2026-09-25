import api from './client';
import type {ContactInfo, UserProfile} from '../types';

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

/** Another user's public profile (no street address). Requires login, like every /api/users call. */
export async function getUserProfile(userId: string): Promise<UserProfile> {
    const res = await api.get(`/api/users/${userId}`);
    return res.data;
}
