import axios from 'axios';
import api from './client';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

/** Emails the logged-in user a link to confirm switching to a dealer account. */
export const requestDealerUpgrade = () =>
    api.post<{ message: string; expiresAt: string }>('/api/auth/dealer-upgrade').then(r => r.data);

/** Redeems the emailed token. Works without login, since the link may be opened anywhere. */
export const confirmDealerUpgrade = (token: string) =>
    api.post<{ userId: string; role: string }>('/api/auth/dealer-upgrade/confirm', {token}).then(r => r.data);

/**
 * Swaps the stored tokens for fresh ones, so a role change shows up in the JWT right away
 * instead of when the access token next expires. No-op when not logged in.
 */
export const refreshSession = async (): Promise<boolean> => {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) return false;
    const res = await axios.post(`${API_URL}/api/auth/refresh`, {refresh_token: refreshToken});
    localStorage.setItem('access_token', res.data.access_token);
    localStorage.setItem('refresh_token', res.data.refresh_token);
    return true;
};
