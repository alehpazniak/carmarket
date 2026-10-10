import {useEffect, useRef, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {CheckCircle2, Loader2, XCircle} from 'lucide-react';
import {confirmDealerUpgrade, refreshSession} from '../api/auth';

/** Landing page of the "confirm dealer account" email link. */
export default function ConfirmDealerUpgrade() {
    const [params] = useSearchParams();
    const token = params.get('token');
    const [result, setResult] = useState<'pending' | 'done' | 'error'>('pending');
    const state = token ? result : 'error';
    // StrictMode runs effects twice in dev; the second confirm would fail because the token is single-use.
    const handled = useRef(false);

    useEffect(() => {
        if (handled.current || !token) return;
        handled.current = true;
        confirmDealerUpgrade(token)
            .then(async () => {
                // Pick up the DEALER role in the JWT now rather than when the access token expires
                try {
                    await refreshSession();
                } catch (err) {
                    console.error(err);
                }
                setResult('done');
            })
            .catch(err => {
                console.error(err);
                setResult('error');
            });
    }, [token]);

    return (
        <div className="min-h-screen bg-avtovo-bg py-16">
            <div className="max-w-md mx-auto px-4">
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-8 text-center space-y-4">
                    {state === 'pending' && (
                        <>
                            <Loader2 size={32} className="animate-spin text-avtovo-accent mx-auto"/>
                            <p className="text-avtovo-text-secondary">Potwierdzanie konta dealera...</p>
                        </>
                    )}
                    {state === 'done' && (
                        <>
                            <CheckCircle2 size={40} className="text-green-400 mx-auto"/>
                            <h1 className="text-xl font-bold text-avtovo-text">Masz teraz konto dealera</h1>
                            <Link to="/moj-profil" className="inline-block text-avtovo-accent hover:underline">
                                Przejdź do profilu
                            </Link>
                        </>
                    )}
                    {state === 'error' && (
                        <>
                            <XCircle size={40} className="text-red-400 mx-auto"/>
                            <h1 className="text-xl font-bold text-avtovo-text">Link jest nieważny</h1>
                            <p className="text-avtovo-text-secondary text-sm">
                                Link wygasł albo został już użyty. Wyślij nowy z poziomu swojego profilu.
                            </p>
                            <Link to="/moj-profil" className="inline-block text-avtovo-accent hover:underline">
                                Przejdź do profilu
                            </Link>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
