import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import { Loader2, Lock, ShieldAlert } from 'lucide-react';
import { clientOrderAccess, getClientOrder } from '../api/importOrders';
import ClientOrderViewCard from '../components/importOrders/ClientOrderViewCard';
import type { ClientOrderView } from '../types/importOrders';

interface StoredToken {
    token: string;
    expiresAt: string;
}

const storageKey = (code: string) => `import-order-token:${code}`;

function readToken(code: string): StoredToken | null {
    try {
        const raw = sessionStorage.getItem(storageKey(code));
        const stored = raw ? (JSON.parse(raw) as StoredToken) : null;
        return stored && new Date(stored.expiresAt) > new Date() ? stored : null;
    } catch {
        return null;
    }
}

function writeToken(code: string, token: StoredToken | null) {
    try {
        if (token) sessionStorage.setItem(storageKey(code), JSON.stringify(token));
        else sessionStorage.removeItem(storageKey(code));
    } catch { /* storage unavailable: the client just types the password again next time */ }
}

/** Adds a <meta> to <head> while the page is open. */
function useHeadMeta(name: string, content: string) {
    useEffect(() => {
        const meta = document.createElement('meta');
        meta.name = name;
        meta.content = content;
        document.head.appendChild(meta);
        return () => meta.remove();
    }, [name, content]);
}

type Phase =
    | { kind: 'loading' }
    | { kind: 'password'; message?: string }
    | { kind: 'locked'; minutes: number }
    | { kind: 'view'; view: ClientOrderView }
    | { kind: 'error' };

function retryMinutes(err: unknown): number {
    if (!axios.isAxiosError(err)) return 15;
    const seconds = Number(err.response?.data?.retryAfterSeconds ?? err.response?.headers?.['retry-after']);
    return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds / 60) : 15;
}

/**
 * /zlecenie/:code — the client's view of their import order, behind the password the dealer sent.
 * Public route; talks to the API through its own axios instance (see api/importOrders.ts).
 */
export default function ClientOrderPage() {
    const { code = '' } = useParams<{ code: string }>();
    useHeadMeta('robots', 'noindex, nofollow');
    // Presigned file URLs and images must not receive this page's URL (it contains the code)
    useHeadMeta('referrer', 'no-referrer');

    const [phase, setPhase] = useState<Phase>(() => (readToken(code) ? { kind: 'loading' } : { kind: 'password' }));
    const [password, setPassword] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const urlsExpireAt = useRef<number | null>(null);

    const load = useCallback(() => {
        const stored = readToken(code);
        if (!stored) {
            setPhase({ kind: 'password', message: 'Sesja wygasła — podaj hasło ponownie.' });
            return;
        }
        getClientOrder(code, stored.token)
            .then(view => {
                urlsExpireAt.current = view.urlsExpireAt ? new Date(view.urlsExpireAt).getTime() : null;
                setPhase({ kind: 'view', view });
            })
            .catch(err => {
                const status = axios.isAxiosError(err) ? err.response?.status : undefined;
                if (status === 401) {
                    // Password reset, access switched off or token expired
                    writeToken(code, null);
                    setPhase({ kind: 'password', message: 'Podaj hasło, aby zobaczyć zlecenie.' });
                } else if (status === 429) {
                    setPhase({ kind: 'locked', minutes: retryMinutes(err) });
                } else {
                    console.error(err);
                    setPhase({ kind: 'error' });
                }
            });
    }, [code]);

    // First load with a stored token
    useEffect(() => {
        if (readToken(code)) load();
    }, [code, load]);

    // Presigned URLs live 5 minutes: refetch the view shortly before they expire, and whenever the
    // client comes back to the tab
    useEffect(() => {
        if (phase.kind !== 'view') return;
        const expires = urlsExpireAt.current;
        const delay = expires ? Math.max(10_000, expires - Date.now() - 20_000) : 4 * 60_000;
        refreshTimer.current = setTimeout(load, delay);
        const onVisible = () => {
            // Every return to the tab: fresh URLs, and a password reset or disabled access shows up right away
            if (document.visibilityState === 'visible') load();
        };
        document.addEventListener('visibilitychange', onVisible);
        return () => {
            if (refreshTimer.current) clearTimeout(refreshTimer.current);
            document.removeEventListener('visibilitychange', onVisible);
        };
    }, [phase, load]);

    const submit = async (e: FormEvent) => {
        e.preventDefault();
        if (!password.trim()) return;
        setSubmitting(true);
        try {
            const granted = await clientOrderAccess(code, password);
            writeToken(code, granted);
            setPassword('');
            setPhase({ kind: 'loading' });
            load();
        } catch (err) {
            const status = axios.isAxiosError(err) ? err.response?.status : undefined;
            if (status === 429) setPhase({ kind: 'locked', minutes: retryMinutes(err) });
            else if (status === 401) setPhase({ kind: 'password', message: 'Nieprawidłowy link lub hasło.' });
            else setPhase({ kind: 'password', message: 'Nie udało się połączyć. Spróbuj ponownie za chwilę.' });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-6 sm:py-10">
            <div className="max-w-xl mx-auto px-4">
                {phase.kind === 'loading' && <div className="flex justify-center py-16"><Loader2 className="animate-spin text-avtovo-accent" /></div>}

                {phase.kind === 'view' && <ClientOrderViewCard view={phase.view} />}

                {phase.kind === 'error' && (
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 text-center space-y-3">
                        <p className="text-avtovo-text">Nie udało się wczytać zlecenia.</p>
                        <button onClick={() => { setPhase({ kind: 'loading' }); load(); }} className="text-avtovo-accent text-sm underline">Spróbuj ponownie</button>
                    </div>
                )}

                {phase.kind === 'locked' && (
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 space-y-3 text-center">
                        <ShieldAlert size={32} className="mx-auto text-amber-400" />
                        <h1 className="text-lg font-semibold text-avtovo-text">Zbyt wiele prób</h1>
                        <p className="text-sm text-avtovo-text-secondary">
                            Ze względów bezpieczeństwa dostęp jest zablokowany na ok. {phase.minutes} min. Spróbuj później albo poproś dealera o nowe hasło.
                        </p>
                        <button onClick={() => setPhase({ kind: 'password' })} className="text-sm text-avtovo-accent underline">Wróć do hasła</button>
                    </div>
                )}

                {phase.kind === 'password' && (
                    <form onSubmit={submit} className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 space-y-4">
                        <div className="text-center space-y-2">
                            <Lock size={28} className="mx-auto text-avtovo-accent" />
                            <h1 className="text-lg font-semibold text-avtovo-text">Twoje zlecenie importu</h1>
                            <p className="text-sm text-avtovo-text-secondary">Wpisz hasło otrzymane od dealera.</p>
                        </div>
                        <input
                            type="password"
                            autoComplete="current-password"
                            autoCapitalize="none"
                            autoCorrect="off"
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            placeholder="np. k7mq-x2rd-p9fa"
                            className="w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-3 text-base text-avtovo-text text-center tracking-wider focus:outline-none focus:border-avtovo-accent"
                            autoFocus
                        />
                        {phase.message && <p className="text-sm text-center text-red-400">{phase.message}</p>}
                        <button type="submit" disabled={submitting || !password.trim()}
                                className="w-full inline-flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg py-3 text-sm font-medium disabled:opacity-60">
                            {submitting && <Loader2 size={16} className="animate-spin" />} Pokaż zlecenie
                        </button>
                    </form>
                )}
            </div>
        </div>
    );
}
