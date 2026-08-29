import { useState } from 'react';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    searchLots,
    getShippingEstimate,
    searchApibaraVehicles,
    type LotSearchParams,
} from '../api/auctions';
import type { AuctionLot, ApibaraShippingResult, ApibaraVehicle } from '../types/auctions';
import AuctionLotCard from '../components/AuctionLotCard';
import ApibaraVehicleBriefCard from '../components/ApibaraVehicleBriefCard';
import { useAuth } from '../context/AuthContext';
import { Search, Ship, ScanSearch, Loader2, Lock } from 'lucide-react';

const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;

export default function AuctionActions() {
    const [lots, setLots] = useState<AuctionLot[]>([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [filters, setFilters] = useState<LotSearchParams>({});
    const navigate = useNavigate();

    const fetchLots = (params: LotSearchParams, targetPage = 0) => {
        setLoading(true);
        searchLots({ ...params, page: targetPage, size: 12 })
            .then(res => {
                setLots(res.content);
                setTotalPages(res.totalPages);
                setPage(res.number);
            })
            .catch(console.error)
            .finally(() => setLoading(false));
    };

    useEffect(() => { fetchLots({}); }, []);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchLots(filters, 0);
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8">
                <div>
                    <h1 className="text-3xl font-bold text-avtovo-text mb-2">Aukcje importowe (USA)</h1>
                    <p className="text-avtovo-text-secondary">
                        Przeglądaj auta z aukcji Copart / IAAI zsynchronizowane przez Apibara, licz koszt importu i sprawdzaj transport.
                    </p>
                </div>

                {/* Filters */}
                <form onSubmit={handleSearch} className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 grid grid-cols-2 sm:grid-cols-5 gap-3">
                    <input
                        placeholder="Marka"
                        value={filters.make ?? ''}
                        onChange={e => setFilters(f => ({ ...f, make: e.target.value || undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <input
                        placeholder="Model"
                        value={filters.model ?? ''}
                        onChange={e => setFilters(f => ({ ...f, model: e.target.value || undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <input
                        type="number"
                        placeholder="Rok od"
                        value={filters.yearFrom ?? ''}
                        onChange={e => setFilters(f => ({ ...f, yearFrom: e.target.value ? Number(e.target.value) : undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <input
                        type="number"
                        placeholder="Rok do"
                        value={filters.yearTo ?? ''}
                        onChange={e => setFilters(f => ({ ...f, yearTo: e.target.value ? Number(e.target.value) : undefined }))}
                        className="bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <button
                        type="submit"
                        className="flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                    >
                        <Search size={15} /> Szukaj
                    </button>
                </form>

                {/* Lot grid */}
                {loading ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {Array.from({ length: 6 }).map((_, i) => (
                            <div key={i} className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden animate-pulse">
                                <div className="aspect-[16/10] bg-avtovo-border" />
                                <div className="p-4 space-y-2">
                                    <div className="h-4 bg-avtovo-border rounded w-3/4" />
                                    <div className="h-4 bg-avtovo-border rounded w-1/2" />
                                </div>
                            </div>
                        ))}
                    </div>
                ) : lots.length === 0 ? (
                    <div className="text-center py-16 text-avtovo-text-secondary">Brak aukcji spełniających kryteria</div>
                ) : (
                    <>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {lots.map(lot => (
                                <AuctionLotCard key={lot.id} lot={lot} onSelect={l => navigate(`/aukcje/lot/${l.id}`)} />
                            ))}
                        </div>
                        {totalPages > 1 && (
                            <div className="flex items-center justify-center gap-2">
                                <button
                                    disabled={page === 0}
                                    onClick={() => fetchLots(filters, page - 1)}
                                    className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text-secondary disabled:opacity-40 hover:border-gray-600 text-sm"
                                >
                                    Poprzednia
                                </button>
                                <span className="text-avtovo-text-secondary text-sm">{page + 1} / {totalPages}</span>
                                <button
                                    disabled={page >= totalPages - 1}
                                    onClick={() => fetchLots(filters, page + 1)}
                                    className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text-secondary disabled:opacity-40 hover:border-gray-600 text-sm"
                                >
                                    Następna
                                </button>
                            </div>
                        )}
                    </>
                )}

                <ApibaraQuickTools />
            </div>
        </div>
    );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
    return (
        <div className={`flex items-center justify-between ${bold ? 'text-avtovo-text font-semibold' : 'text-avtovo-text-secondary'}`}>
            <span>{label}</span>
            <span>{value}</span>
        </div>
    );
}

function ApibaraQuickTools() {
    const navigate = useNavigate();
    const { isAuthenticated, isLoading: authLoading, loginWithGoogle } = useAuth();

    const [vin, setVin] = useState('');
    const [lotNumber, setLotNumber] = useState('');
    const [shipping, setShipping] = useState<ApibaraShippingResult | null>(null);
    const [shippingLoading, setShippingLoading] = useState(false);
    const [shippingError, setShippingError] = useState<string | null>(null);

    const [identifier, setIdentifier] = useState('');
    const [searchMake, setSearchMake] = useState('');
    const [searchModel, setSearchModel] = useState('');
    const [results, setResults] = useState<ApibaraVehicle[] | null>(null);
    const [searchLoading, setSearchLoading] = useState(false);
    const [searchError, setSearchError] = useState<string | null>(null);

    const handleShipping = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!vin && !lotNumber) return;
        setShippingLoading(true);
        setShippingError(null);
        setShipping(null);
        try {
            const result = await getShippingEstimate({ vin: vin || undefined, lot_number: lotNumber || undefined });
            if (!result.data) setShippingError('Brak danych transportowych dla podanego pojazdu');
            setShipping(result);
        } catch {
            setShippingError('Nie udało się pobrać wyceny transportu (limit Apibara lub błędne dane)');
        } finally {
            setShippingLoading(false);
        }
    };

    const handleIdentifierLookup = (e: React.FormEvent) => {
        e.preventDefault();
        if (!identifier.trim()) return;
        // Jump straight to the detail page — no need to burn an extra Apibara call
        // just to list a single exact-match result.
        navigate(`/aukcje/pojazd/${encodeURIComponent(identifier.trim())}`);
    };

    const handleVehicleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!searchMake && !searchModel) return;
        setSearchLoading(true);
        setSearchError(null);
        setResults(null);
        try {
            const result = await searchApibaraVehicles({
                ...(searchMake ? { make: searchMake } : {}),
                ...(searchModel ? { model: searchModel } : {}),
            });
            if (result.data.length === 0) setSearchError('Brak pojazdów spełniających kryteria');
            setResults(result.data);
        } catch {
            setSearchError('Nie udało się wyszukać pojazdów (limit Apibara lub błędne dane)');
        } finally {
            setSearchLoading(false);
        }
    };

    if (authLoading) return null;

    if (!isAuthenticated) {
        return (
            <div className="border-t border-avtovo-border pt-8">
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-8 text-center space-y-3">
                    <Lock size={28} className="text-avtovo-text-secondary mx-auto" />
                    <h2 className="text-lg font-semibold text-avtovo-text">Szybkie sprawdzenie w Apibara wymaga zalogowania</h2>
                    <p className="text-avtovo-text-secondary text-sm max-w-md mx-auto">
                        Wyszukiwanie pojazdów na żywo, podgląd VIN/numeru lota oraz wycena transportu korzystają z płatnego
                        limitu Apibara i są dostępne tylko dla zalogowanych użytkowników.
                    </p>
                    <button
                        onClick={loginWithGoogle}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-5 py-2 text-sm font-medium transition-colors"
                    >
                        Zaloguj się
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="border-t border-avtovo-border pt-8 space-y-6">
            <div>
                <h2 className="text-xl font-bold text-avtovo-text mb-1">Szybkie sprawdzenie w Apibara</h2>
                <p className="text-avtovo-text-secondary text-sm">
                    Zapytania wysyłane bezpośrednio do Apibara (limit darmowego planu: 100/miesiąc) — używaj z rozwagą.
                </p>
            </div>

            {/* Shipping estimator */}
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-5">
                <h3 className="text-avtovo-text font-semibold mb-3 flex items-center gap-2">
                    <Ship size={16} className="text-avtovo-accent" /> Koszt transportu z aukcji do portu
                </h3>
                <form onSubmit={handleShipping} className="flex flex-col sm:flex-row gap-2">
                    <input
                        placeholder="VIN"
                        value={vin}
                        onChange={e => setVin(e.target.value)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <input
                        placeholder="Nr lota"
                        value={lotNumber}
                        onChange={e => setLotNumber(e.target.value)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <button
                        type="submit"
                        disabled={shippingLoading}
                        className="flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap"
                    >
                        {shippingLoading ? <Loader2 size={15} className="animate-spin" /> : <Ship size={15} />} Sprawdź
                    </button>
                </form>
                {shippingError && <p className="text-red-400 text-sm mt-3">{shippingError}</p>}
                {shipping?.data && (
                    <div className="mt-4 bg-avtovo-bg border border-avtovo-border rounded-lg p-4 text-sm space-y-1.5 max-w-md">
                        <Row label="Pojazd" value={shipping.data.vehicle.title} />
                        <Row label="Lokalizacja aukcji" value={shipping.data.auctionLocation.display ?? '—'} />
                        <Row label="Rekomendowany port" value={shipping.data.shipping.recommended_port ?? '—'} />
                        <Row label="Cena transportu" value={USD(shipping.data.shipping.recommended_price_usd)} bold />
                    </div>
                )}
            </div>

            {/* Exact VIN / lot number / slug lookup */}
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-5">
                <h3 className="text-avtovo-text font-semibold mb-3 flex items-center gap-2">
                    <ScanSearch size={16} className="text-avtovo-accent" /> Znajdź po VIN lub numerze lota
                </h3>
                <form onSubmit={handleIdentifierLookup} className="flex flex-col sm:flex-row gap-2">
                    <input
                        placeholder="VIN, numer lota lub slug"
                        value={identifier}
                        onChange={e => setIdentifier(e.target.value)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <button
                        type="submit"
                        className="flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap"
                    >
                        <ScanSearch size={15} /> Znajdź
                    </button>
                </form>
            </div>

            {/* Vehicle search by make/model */}
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-5">
                <h3 className="text-avtovo-text font-semibold mb-3 flex items-center gap-2">
                    <Search size={16} className="text-avtovo-accent" /> Szukaj pojazdów po marce i modelu
                </h3>
                <form onSubmit={handleVehicleSearch} className="flex flex-col sm:flex-row gap-2">
                    <input
                        placeholder="Marka"
                        value={searchMake}
                        onChange={e => setSearchMake(e.target.value)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <input
                        placeholder="Model"
                        value={searchModel}
                        onChange={e => setSearchModel(e.target.value)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                    />
                    <button
                        type="submit"
                        disabled={searchLoading}
                        className="flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors whitespace-nowrap"
                    >
                        {searchLoading ? <Loader2 size={15} className="animate-spin" /> : <Search size={15} />} Szukaj
                    </button>
                </form>
                {searchError && <p className="text-red-400 text-sm mt-3">{searchError}</p>}
                {results && results.length > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
                        {results.map(v => (
                            <ApibaraVehicleBriefCard
                                key={`${v.platform}-${v.lotNumber}`}
                                vehicle={v}
                                onSelect={vehicle => navigate(`/aukcje/pojazd/${encodeURIComponent(vehicle.vin)}`)}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
