import { useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';

export default function AuthCallback() {
    const navigate = useNavigate();
    // StrictMode runs effects twice in dev. The second run would find post_login_path
    // already consumed and send the user home instead of back to the listing.
    const handled = useRef(false);
    // auth-service redirects here with ?error=blocked when a blocked account logs in; the axios
    // interceptor does the same when /auth/refresh answers 403
    const blocked = new URLSearchParams(window.location.search).get('error') === 'blocked';

    useEffect(() => {
        if (handled.current || blocked) return;
        handled.current = true;
        const params = new URLSearchParams(window.location.search);
        const accessToken = params.get('access_token');
        const refreshToken = params.get('refresh_token');
        const userId = params.get('user_id');

        if (accessToken && refreshToken) {
            localStorage.setItem('access_token', accessToken);
            localStorage.setItem('refresh_token', refreshToken);
            if (userId) localStorage.setItem('user_id', userId);
            let next = '/';
            try {
                const saved = sessionStorage.getItem('post_login_path');
                sessionStorage.removeItem('post_login_path');
                // Only same-site relative paths, never another origin
                if (saved && saved.startsWith('/') && !saved.startsWith('//')) next = saved;
            } catch { /* ignore */ }
            navigate(next, { replace: true });
        } else {
            navigate('/', { replace: true });
        }
    }, [navigate, blocked]);

    if (blocked) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-avtovo-bg px-4">
                <div className="max-w-md text-center bg-avtovo-card border border-avtovo-border rounded-xl p-8 space-y-4">
                    <ShieldOff size={36} className="mx-auto text-red-400" />
                    <h1 className="text-xl font-semibold text-avtovo-text">Twoje konto zostało zablokowane</h1>
                    <p className="text-sm text-avtovo-text-secondary">
                        Nie możesz się zalogować. Jeśli uważasz, że to pomyłka, skontaktuj się z obsługą serwisu.
                    </p>
                    <Link to="/" className="inline-block text-sm text-avtovo-accent hover:underline">Wróć na stronę główną</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-avtovo-bg">
            <div className="text-center">
                <div className="w-10 h-10 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin mx-auto mb-4" />
                <p className="text-avtovo-text-secondary">Logowanie...</p>
            </div>
        </div>
    );
}