import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import api from '../api/client';
import type {User} from '../types';
import {rolesFromToken} from '../utils/jwt';

interface AuthContextType {
    user: User | null;
    isLoading: boolean;
    isAuthenticated: boolean;
    loginWithGoogle: () => void;
    logout: () => Promise<void>;
    /**
     * Whether the current access token carries the role (decoded, not verified). Read at call time, so
     * it follows silent token refreshes; DEALER appears after the refresh in ConfirmDealerUpgrade.
     */
    hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const token = localStorage.getItem('access_token');
        if (token) {
            fetchUser();
        } else {
            setIsLoading(false);
        }
    }, []);

    const fetchUser = async () => {
        try {
            const res = await api.get('/api/users/me');
            setUser(res.data);
        } catch {
            localStorage.clear();
        } finally {
            setIsLoading(false);
        }
    };

    const loginWithGoogle = () => {
        // Remember where the user was so AuthCallback can send them back after login
        try {
            sessionStorage.setItem('post_login_path', window.location.pathname + window.location.search);
        } catch { /* storage unavailable — fall back to home */ }
        window.location.href = 'http://localhost:8081/auth/oauth2/authorization/google';
    };

    const logout = async () => {
        try {
            const refreshToken = localStorage.getItem('refresh_token');
            if (refreshToken) {
                await api.post('/api/auth/logout', { refresh_token: refreshToken });
            }
        } finally {
            localStorage.clear();
            setUser(null);
        }
    };

    const hasRole = (role: string) => !!user && rolesFromToken(localStorage.getItem('access_token')).includes(role);

    return (
        <AuthContext.Provider value={{
            user,
            isLoading,
            isAuthenticated: !!user,
            loginWithGoogle,
            logout,
            hasRole,
        }}>
            {children}
        </AuthContext.Provider>
    );
}

// eslint-disable-next-line react-refresh/only-export-components -- provider and its hook belong together
export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used within AuthProvider');
    return ctx;
};