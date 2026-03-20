import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export default function AuthCallback() {
    const navigate = useNavigate();

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const accessToken = params.get('access_token');
        const refreshToken = params.get('refresh_token');
        const userId = params.get('user_id');

        if (accessToken && refreshToken) {
            localStorage.setItem('access_token', accessToken);
            localStorage.setItem('refresh_token', refreshToken);
            if (userId) localStorage.setItem('user_id', userId);
            navigate('/', { replace: true });
        } else {
            navigate('/', { replace: true });
        }
    }, [navigate]);

    return (
        <div className="min-h-screen flex items-center justify-center bg-avtovo-bg">
            <div className="text-center">
                <div className="w-10 h-10 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin mx-auto mb-4" />
                <p className="text-avtovo-text-secondary">Logowanie...</p>
            </div>
        </div>
    );
}