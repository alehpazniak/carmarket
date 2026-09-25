import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const api = axios.create({
    baseURL: API_URL,
    headers: { 'Content-Type': 'application/json' },
});

// Attach token to every request
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('access_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Auto-refresh on 401
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config;
        if (error.response?.status === 401 && !original._retry) {
            original._retry = true;
            const refreshToken = localStorage.getItem('refresh_token');
            if (refreshToken) {
                try {
                    const res = await axios.post(`${API_URL}/api/auth/refresh`, {
                        refresh_token: refreshToken,
                    });
                    const { access_token, refresh_token } = res.data;
                    localStorage.setItem('access_token', access_token);
                    localStorage.setItem('refresh_token', refresh_token);
                    original.headers.Authorization = `Bearer ${access_token}`;
                    return api(original);
                } catch {
                    localStorage.clear();
                    window.location.href = '/';
                }
            } else {
                localStorage.clear();
                window.location.href = '/';
            }
        }
        return Promise.reject(error);
    }
);

export default api;