import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, Search } from 'lucide-react';
import { apibaraErrorMessage, isNotFound, lookupLot, NOT_FOUND_MESSAGE, resolveApibaraUrl } from '../../api/auctions';
import { useAuth } from '../../context/AuthContext';

/*
 * "Numer lota, VIN lub link" above the /aukcje filters. Our database first (GET /lots/lookup, free); a car we
 * don't have opens the live page /aukcje/pojazd/:identifier (one Apibara request there). A link is resolved
 * through Apibara first, then the page opens with the VIN or lot number, so its URL stays clean; the backend
 * caches that answer, so the page itself doesn't spend another request.
 */
export default function LotLookupBar() {
    const navigate = useNavigate();
    const { isAuthenticated, isLoading: authLoading } = useAuth();
    const [query, setQuery] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const q = query.trim();
        if (!q || busy) return;
        setBusy(true);
        setError(null);
        try {
            const found = await lookupLot(q);
            if (found.ids.length > 0) {
                navigate(`/aukcje/lot/${found.ids[0]}`);
            } else if (found.identifier) {
                navigate(`/aukcje/pojazd/${encodeURIComponent(found.identifier)}`);
            } else if (found.kind === 'COPART_URL' || found.kind === 'IAAI_URL' || found.kind === 'URL') {
                const { data } = await resolveApibaraUrl(q);
                // A masked Copart VIN can't be looked up; the lot number always can
                const id = data?.vin && data.vin.length === 17 && !data.vin.includes('*') ? data.vin : data?.lot_number;
                if (id) navigate(`/aukcje/pojazd/${encodeURIComponent(id)}`);
                else setError(NOT_FOUND_MESSAGE);
            } else {
                setError(NOT_FOUND_MESSAGE);
            }
        } catch (err) {
            setError(isNotFound(err) ? NOT_FOUND_MESSAGE
                : apibaraErrorMessage(err, isAuthenticated, 'Nie udało się wyszukać auta. Spróbuj ponownie.'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="space-y-2">
            <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row gap-2">
                <div className="relative flex-1">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-avtovo-muted pointer-events-none" />
                    <input
                        value={query}
                        onChange={e => setQuery(e.target.value)}
                        placeholder="Numer lota, VIN lub link z Copart/IAAI"
                        aria-label="Numer lota, VIN lub link z Copart/IAAI"
                        className="w-full bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg pl-9 pr-3 py-2.5 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                </div>
                <button
                    type="submit"
                    // a logged-in user's lookup must carry the token, or it counts against the anonymous limit
                    disabled={busy || authLoading || !query.trim()}
                    className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg bg-avtovo-accent text-white text-sm font-semibold hover:bg-avtovo-accent-hover disabled:opacity-50 transition-colors"
                >
                    {busy && <Loader2 size={16} className="animate-spin" />}
                    Znajdź
                </button>
            </form>
            {error && <p className="text-sm text-red-400">{error}</p>}
        </div>
    );
}
