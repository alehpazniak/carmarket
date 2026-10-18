import { useEffect, useState } from 'react';
import axios from 'axios';
import { Loader2, Lock, Truck } from 'lucide-react';
import { getW8Locations, getW8Quote } from '../../api/auctions';
import type { LengthClass, PolandZone, W8Auction, W8Location, W8ShippingQuote } from '../../types/auctions';
import { useAuth } from '../../context/AuthContext';
import W8QuoteBreakdown from './W8QuoteBreakdown';
import W8ShippingFields from './W8ShippingFields';

const INPUT = 'w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent';

const AUCTIONS: { value: W8Auction; label: string }[] = [
    { value: 'COPART', label: 'Copart' },
    { value: 'IAAI', label: 'IAAI' },
    { value: 'MANHEIM', label: 'Manheim' },
    { value: 'ADESA', label: 'ADESA' },
];

/** "Kalkulator transportu W8": yard → Poland from W8's own price table (no Apibara quota). Logged-in users only. */
export default function W8TransportCalculator() {
    const { isAuthenticated, loginWithGoogle } = useAuth();

    const [auction, setAuction] = useState<W8Auction>('COPART');
    const [query, setQuery] = useState('');
    const [selected, setSelected] = useState<W8Location | null>(null);
    const [suggestions, setSuggestions] = useState<W8Location[]>([]);
    const [suv, setSuv] = useState(false);
    const [zone, setZone] = useState<PolandZone>('A');
    const [length, setLength] = useState<LengthClass>('STANDARD');
    const [quote, setQuote] = useState<W8ShippingQuote | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Debounced autocomplete; nothing to fetch once a suggestion is picked or below 2 characters
    useEffect(() => {
        const q = query.trim();
        if (!isAuthenticated || selected || q.length < 2) return;
        let cancelled = false;
        const timer = setTimeout(() => {
            getW8Locations(q, auction)
                .then(found => { if (!cancelled) setSuggestions(found); })
                .catch(() => { if (!cancelled) setSuggestions([]); });
        }, 300);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [query, auction, selected, isAuthenticated]);

    const shown = !selected && query.trim().length >= 2 ? suggestions : [];

    const pick = (location: W8Location) => {
        setSelected(location);
        setQuery(location.location);
        setAuction(location.auction);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const location = selected?.location ?? query.trim();
        if (!location) return;
        setLoading(true);
        setError(null);
        setQuote(null);
        try {
            setQuote(await getW8Quote({ location, auction, suv, zone, length }));
        } catch (err) {
            setError(axios.isAxiosError(err) && err.response?.status === 404
                ? 'W8 nie ma ceny transportu dla tej lokalizacji.'
                : 'Nie udało się policzyć transportu.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-5">
            <h3 className="text-avtovo-text font-semibold mb-1 flex items-center gap-2">
                <Truck size={16} className="text-avtovo-accent" /> Kalkulator transportu W8
            </h3>
            <p className="text-avtovo-text-secondary text-sm mb-3">
                Holowanie z placu aukcyjnego, fracht do Rotterdamu, spedycja i transport do Polski według cennika W8 Shipping.
            </p>
            {!isAuthenticated ? (
                <div className="bg-avtovo-bg border border-avtovo-border rounded-lg p-4 text-center space-y-2">
                    <Lock size={20} className="text-avtovo-text-secondary mx-auto" />
                    <p className="text-avtovo-text-secondary text-sm">Zaloguj się, aby skorzystać z kalkulatora transportu.</p>
                    <button
                        onClick={loginWithGoogle}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                    >
                        Zaloguj się
                    </button>
                </div>
            ) : (
                <>
                    <form onSubmit={handleSubmit} className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                        <div>
                            <label className="text-xs text-avtovo-text-secondary">Aukcja</label>
                            <select
                                value={auction}
                                onChange={e => { setAuction(e.target.value as W8Auction); setSelected(null); }}
                                className={INPUT}
                            >
                                {AUCTIONS.map(a => <option key={a.value} value={a.value}>{a.label}</option>)}
                            </select>
                        </div>
                        <div className="relative">
                            <label className="text-xs text-avtovo-text-secondary">Lokalizacja (plac aukcyjny)</label>
                            <input
                                required
                                value={query}
                                onChange={e => { setQuery(e.target.value); setSelected(null); }}
                                placeholder="np. Chicago South"
                                autoComplete="off"
                                className={INPUT}
                            />
                            {shown.length > 0 && (
                                <ul className="absolute z-20 left-0 right-0 mt-1 max-h-64 overflow-y-auto bg-avtovo-card border border-avtovo-border rounded-lg shadow-lg">
                                    {shown.map(l => (
                                        <li key={`${l.auction}-${l.location}`}>
                                            <button
                                                type="button"
                                                onClick={() => pick(l)}
                                                className="w-full text-left px-3 py-2 text-sm text-avtovo-text hover:bg-avtovo-bg"
                                            >
                                                {l.location}
                                                <span className="text-avtovo-text-secondary text-xs"> · {l.auction}</span>
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                        <W8ShippingFields zone={zone} length={length} onZoneChange={setZone} onLengthChange={setLength} />
                        <label className="flex items-center gap-2 text-sm text-avtovo-text-secondary">
                            <input type="checkbox" checked={suv} onChange={e => setSuv(e.target.checked)} />
                            To SUV (1/3 kontenera)
                        </label>
                        <button
                            type="submit"
                            disabled={loading}
                            className="sm:col-span-2 flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-medium transition-colors"
                        >
                            {loading && <Loader2 size={15} className="animate-spin" />} Policz transport
                        </button>
                    </form>
                    {error && <p className="text-red-400 text-sm mt-3">{error}</p>}
                    {quote && (
                        <div className="mt-4 bg-avtovo-bg border border-avtovo-border rounded-lg p-4 text-sm max-w-xl">
                            <W8QuoteBreakdown quote={quote} showTotal />
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
