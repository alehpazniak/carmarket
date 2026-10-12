import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Runs a memoized loader (useCallback) and keeps its result. `loading` is true until the current
 * loader has answered, so changing filters shows the loading state; `reload` re-runs it in place.
 */
export function useLoader<T>(loader: () => Promise<T>) {
    const [state, setState] = useState<{ source?: () => Promise<T>; data?: T; error?: unknown }>({});
    const [nonce, setNonce] = useState(0);

    useEffect(() => {
        let cancelled = false;
        loader().then(
            data => { if (!cancelled) setState({ source: loader, data }); },
            error => {
                console.error(error);
                if (!cancelled) setState({ source: loader, error });
            });
        return () => { cancelled = true; };
    }, [loader, nonce]);

    const reload = useCallback(() => setNonce(n => n + 1), []);
    const current = state.source === loader;
    return {
        data: current ? state.data : undefined,
        error: current ? state.error : undefined,
        loading: !current,
        reload,
    };
}

/**
 * Filters, sort and page kept in the URL query string, so a filtered list can be bookmarked and the
 * back button restores it. Changing anything but the page goes back to page 0.
 */
export function useQueryState<K extends string>(defaults: Record<K, string>) {
    const [params, setParams] = useSearchParams();
    const values = Object.fromEntries(
        Object.entries(defaults).map(([k, d]) => [k, params.get(k) ?? d])) as Record<K, string>;

    const update = useCallback((patch: Partial<Record<K | 'page', string | undefined>>) => {
        setParams(prev => {
            const next = new URLSearchParams(prev);
            for (const [k, v] of Object.entries(patch) as [string, string | undefined][]) {
                if (v === undefined || v === '') next.delete(k);
                else next.set(k, v);
            }
            if (!('page' in patch)) next.delete('page');
            return next;
        }, { replace: true });
    }, [setParams]);

    return { values, update, query: params.toString() };
}

/** Same value, updated only after `delay` ms without changes. */
export function useDebounced<T>(value: T, delay = 300): T {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const t = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(t);
    }, [value, delay]);
    return debounced;
}

/** Backend messages (English) the admin panel shows in Polish. */
const KNOWN_ERRORS: Record<string, string> = {
    "You can't change your own role": 'Nie możesz zmienić własnej roli.',
    "You can't block yourself": 'Nie możesz zablokować własnego konta.',
    "The last active admin can't be demoted or blocked": 'Nie można odebrać uprawnień ani zablokować ostatniego aktywnego administratora.',
    'Validation failed': 'Uzupełnij poprawnie wszystkie pola (powód: co najmniej 5 znaków).',
};

/** Message from an axios error body (ProblemDetail detail / {error} / {message}), else the fallback. */
export function errorMessage(error: unknown, fallback = 'Coś poszło nie tak'): string {
    const response = (error as { response?: { status?: number; data?: { detail?: string; error?: string; message?: string } } })?.response;
    if (response?.status === 403) return 'Brak uprawnień do tej operacji.';
    const text = response?.data?.detail || response?.data?.message || response?.data?.error;
    return (text && KNOWN_ERRORS[text]) || text || fallback;
}
