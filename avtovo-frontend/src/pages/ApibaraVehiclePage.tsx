import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getApibaraVehicle, getVehicleHistory, getRelatedVehicles, calculateApibaraMaxBid } from '../api/auctions';
import type { ApibaraVehicleDetail, ApibaraHistoryEntry, ApibaraRelatedVehicle, MaxBidResult, FuelType } from '../types/auctions';
import PhotoGallery, { type GalleryPhoto } from '../components/PhotoGallery';
import { useAuth } from '../context/AuthContext';
import {
    ChevronLeft, MapPin, Key, ShieldCheck, Fuel, Cog, Palette, FileCheck2,
    Lock, Clock, Gavel, Copy, Check, Gauge, Timer, TrendingUp, Loader2,
} from 'lucide-react';

type Vehicle = NonNullable<ApibaraVehicleDetail['data']>;

const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
const PLN = (v: number | undefined) => v == null ? '—' : `${v.toLocaleString('pl-PL', { maximumFractionDigits: 0 })} zł`;

// "2.0L" → 2000 (cm³). Apibara's engine size is free text — best effort only.
function parseEngineCapacityCm3(sizeL?: string): number | undefined {
    const match = sizeL?.match(/([\d.]+)/);
    if (!match) return undefined;
    const liters = parseFloat(match[1]);
    return Number.isFinite(liters) ? Math.round(liters * 1000) : undefined;
}

// Apibara's fuel_type is free text (e.g. "Gasoline", "Plug-in Hybrid Electric") — best effort mapping,
// checked most-specific first since a plug-in hybrid's text also contains "hybrid" and "electric".
function guessFuelType(raw?: string): FuelType {
    const s = (raw ?? '').toLowerCase();
    if (s.includes('plug')) return 'PLUGIN_HYBRID';
    if (s.includes('hybrid')) return 'HYBRID';
    if (s.includes('electric')) return 'ELECTRIC';
    if (s.includes('diesel')) return 'DIESEL';
    return 'PETROL';
}

// Apibara's body_style is free text (e.g. "SUV", "Sedan") — best effort mapping, used only to
// pre-fill the shipping-delivery cost tier (SUVs cost more to ship).
function guessIsSuv(bodyStyle?: string): boolean {
    return (bodyStyle ?? '').toLowerCase().includes('suv');
}

const AUCTION_STATE_LABELS: Record<string, string> = {
    live: 'Trwa',
    finished: 'Zakończona',
    upcoming: 'Nadchodząca',
};

// Countdown string ("2d 5h 30m") for a live/upcoming auction; "0d 0h 0m" once it's passed.
function timeRemaining(auctionAt?: string): string | null {
    if (!auctionAt) return null;
    const diff = new Date(auctionAt).getTime() - Date.now();
    if (diff <= 0) return '0d 0h 0m';
    const days = Math.floor(diff / 86_400_000);
    const hours = Math.floor((diff % 86_400_000) / 3_600_000);
    const minutes = Math.floor((diff % 3_600_000) / 60_000);
    return `${days}d ${hours}h ${minutes}m`;
}

export default function ApibaraVehiclePage() {
    const { identifier } = useParams<{ identifier: string }>();
    const navigate = useNavigate();
    const { isAuthenticated, isLoading: authLoading, loginWithGoogle } = useAuth();
    const [vehicle, setVehicle] = useState<Vehicle | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    // null = not loaded yet (button shown), [] = loaded but empty — kept separate from `history`
    // so the "Pokaż" button knows whether to fetch, since these are real, quota-metered calls
    // (free tier is 100 req/month) and shouldn't fire just from viewing the page.
    const [history, setHistory] = useState<ApibaraHistoryEntry[] | null>(null);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [related, setRelated] = useState<ApibaraRelatedVehicle[] | null>(null);
    const [loadingRelated, setLoadingRelated] = useState(false);
    const [vinCopied, setVinCopied] = useState(false);

    const [budgetPln, setBudgetPln] = useState('');
    const [repairCostPln, setRepairCostPln] = useState('');
    const [engineCapacityCm3, setEngineCapacityCm3] = useState('');
    const [fuelType, setFuelType] = useState<FuelType>('PETROL');
    const [suv, setSuv] = useState(false);
    const [maxBidResult, setMaxBidResult] = useState<MaxBidResult | null>(null);
    const [maxBidCalculating, setMaxBidCalculating] = useState(false);
    const [maxBidError, setMaxBidError] = useState<string | null>(null);

    // Guards the quota-metered vehicle fetch against firing twice for the same identifier —
    // React.StrictMode double-invokes effects on mount in dev, which would otherwise burn two
    // Apibara requests (out of a 100/month free-tier quota) for a single page view.
    const fetchedIdentifierRef = useRef<string | null>(null);

    useEffect(() => {
        // Apibara live-lookup requires a logged-in user (see gateway/auction-import-service
        // security config) — don't fire the request anonymously, it would just 401.
        if (!identifier || authLoading || !isAuthenticated) return;
        if (fetchedIdentifierRef.current === identifier) return;
        fetchedIdentifierRef.current = identifier;
        getApibaraVehicle(identifier)
            .then(result => {
                if (!result.data) setError('Nie znaleziono pojazdu');
                setVehicle(result.data);
                setEngineCapacityCm3(prev => prev || String(parseEngineCapacityCm3(result.data?.vehicle_specs?.engine?.size_l) ?? ''));
                setFuelType(guessFuelType(result.data?.vehicle_specs?.fuel_type));
                setSuv(guessIsSuv(result.data?.vehicle_specs?.body_style));
            })
            .catch(() => setError('Nie udało się pobrać danych pojazdu (limit Apibara lub błędne dane)'))
            .finally(() => setLoading(false));
    }, [identifier, authLoading, isAuthenticated]);

    const handleLoadHistory = () => {
        if (!identifier) return;
        setLoadingHistory(true);
        getVehicleHistory(identifier)
            .then(result => setHistory(Array.isArray(result.data) ? result.data : result.data?.items ?? []))
            .catch(() => setHistory([]))
            .finally(() => setLoadingHistory(false));
    };

    const handleLoadRelated = () => {
        if (!identifier) return;
        setLoadingRelated(true);
        getRelatedVehicles(identifier)
            .then(result => setRelated([
                ...(result.data?.source ?? []),
                ...(result.data?.upcoming ?? []),
                ...(result.data?.past ?? []),
            ].slice(0, 8)))
            .catch(() => setRelated([]))
            .finally(() => setLoadingRelated(false));
    };

    const handleCalculateMaxBid = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!identifier || !budgetPln) return;
        setMaxBidCalculating(true);
        setMaxBidError(null);
        try {
            const result = await calculateApibaraMaxBid(identifier, {
                budgetPln: Number(budgetPln),
                estimatedRepairCostPln: repairCostPln ? Number(repairCostPln) : undefined,
                engineCapacityCm3: engineCapacityCm3 ? Number(engineCapacityCm3) : undefined,
                fuelType,
                suv,
            });
            setMaxBidResult(result);
        } catch {
            setMaxBidError('Nie udało się policzyć maksymalnej ceny (limit Apibara lub błędne dane)');
        } finally {
            setMaxBidCalculating(false);
        }
    };

    if (authLoading) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
        </div>
    );

    if (!isAuthenticated) return (
        <div className="min-h-screen bg-avtovo-bg flex flex-col items-center justify-center gap-3 px-4">
            <Lock size={28} className="text-avtovo-text-secondary" />
            <p className="text-avtovo-text-secondary text-center max-w-sm">
                Podgląd pojazdu na żywo z Apibara wymaga zalogowania.
            </p>
            <button
                onClick={loginWithGoogle}
                className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-5 py-2 text-sm font-medium transition-colors"
            >
                Zaloguj się
            </button>
            <button onClick={() => navigate(-1)} className="text-avtovo-accent text-sm hover:underline">Wróć</button>
        </div>
    );

    if (loading) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
        </div>
    );

    if (error || !vehicle) return (
        <div className="min-h-screen bg-avtovo-bg flex flex-col items-center justify-center gap-3">
            <p className="text-avtovo-text-secondary">{error ?? 'Nie znaleziono pojazdu'}</p>
            <button onClick={() => navigate(-1)} className="text-avtovo-accent text-sm hover:underline">Wróć</button>
        </div>
    );

    const photos: GalleryPhoto[] = vehicle.media?.items?.length
        ? vehicle.media.items.map(item => ({ thumb: item.thumb ?? item.large ?? '', large: item.large ?? item.thumb ?? '' }))
        : (vehicle.media?.thumbs ?? []).map(t => ({ thumb: t, large: t }));

    const isEnded = vehicle.auction?.state === 'finished';
    const countdown = !isEnded ? timeRemaining(vehicle.auction?.auctionAt) : null;
    const auctionStateLabel = vehicle.auction?.state ? (AUCTION_STATE_LABELS[vehicle.auction.state] ?? vehicle.auction.state) : '—';

    const copyVin = () => {
        navigator.clipboard.writeText(vehicle.vin).then(() => {
            setVinCopied(true);
            setTimeout(() => setVinCopied(false), 1500);
        });
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-6xl mx-auto px-4 space-y-6">
                <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text transition-colors">
                    <ChevronLeft size={18} />
                    Wróć
                </button>

                {/* Title + badges */}
                <div>
                    <div className="flex items-center gap-2 mb-2">
                        <span className="text-xs font-medium px-2 py-1 rounded-md bg-avtovo-card border border-avtovo-border text-avtovo-text-secondary uppercase">
                            {vehicle.platform}
                        </span>
                        <span className={`text-xs font-medium px-2 py-1 rounded-md ${isEnded ? 'bg-avtovo-border text-avtovo-text-secondary' : 'bg-avtovo-accent/15 text-avtovo-accent'}`}>
                            {auctionStateLabel}
                        </span>
                        {vehicle.auction?.formatted && (
                            <span className="text-xs text-avtovo-text-secondary">{vehicle.auction.formatted}</span>
                        )}
                    </div>
                    <h1 className="text-2xl font-bold text-avtovo-text mb-1">{vehicle.title}</h1>
                    <div className="flex items-center gap-3 text-avtovo-text-secondary text-sm">
                        <span>Lot #{vehicle.lotNumber}</span>
                        <span className="flex items-center gap-1.5">
                            VIN {vehicle.vin}
                            <button
                                onClick={copyVin}
                                className="text-avtovo-text-secondary hover:text-avtovo-accent transition-colors"
                                aria-label="Kopiuj VIN"
                            >
                                {vinCopied ? <Check size={14} className="text-emerald-400" /> : <Copy size={14} />}
                            </button>
                        </span>
                        {vehicle.location?.display && (
                            <span className="flex items-center gap-1"><MapPin size={13} /> {vehicle.location.display}</span>
                        )}
                    </div>
                </div>

                {/* Gallery + key info */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2">
                        <PhotoGallery photos={photos} alt={vehicle.title} />
                    </div>

                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-5">
                        <h2 className="text-avtovo-text font-semibold mb-3 text-sm uppercase tracking-wide text-avtovo-text-secondary">Kluczowe informacje</h2>
                        <div className="grid grid-cols-2 gap-3">
                            <KeyStat label="Aktualna oferta" value={USD(vehicle.pricing?.current_bid_usd)} accent />
                            <KeyStat label="Ostatnia sprzedaż" value={USD(vehicle.pricing?.last_sold_price_usd)} />
                            <KeyStat
                                label="Szac. koszt"
                                value={vehicle.pricing?.estimated_cost?.text
                                    ?? (vehicle.pricing?.estimated_cost?.from != null && vehicle.pricing?.estimated_cost?.to != null
                                        ? `${USD(vehicle.pricing.estimated_cost.from)} – ${USD(vehicle.pricing.estimated_cost.to)}`
                                        : '—')}
                            />
                            {countdown ? (
                                <KeyStat label="Pozostały czas" value={countdown} icon={<Timer size={13} />} />
                            ) : (
                                <KeyStat label="Status aukcji" value={auctionStateLabel} />
                            )}
                            <KeyStat
                                label="Przebieg"
                                value={vehicle.odometer?.mi != null ? `${vehicle.odometer.mi.toLocaleString('en-US')} mi` : '—'}
                                icon={<Gauge size={13} />}
                            />
                            <KeyStat label="Stan" value={vehicle.condition?.run_condition?.label ?? '—'} />
                            <KeyStat label="Silnik" value={vehicle.vehicle_specs?.engine?.raw ?? '—'} />
                            <KeyStat label="Paliwo / Skrzynia" value={[vehicle.vehicle_specs?.fuel_type, vehicle.vehicle_specs?.transmission].filter(Boolean).join(' / ') || '—'} />
                            <KeyStat label="Napęd" value={vehicle.vehicle_specs?.drive_type ?? '—'} />
                            <KeyStat label="Uszkodzenia" value={[vehicle.condition?.primary_damage, vehicle.condition?.secondary_damage].filter(Boolean).join(' · ') || '—'} />
                        </div>
                    </div>
                </div>

                {/* Lot details — one card, grouped into condition / physical / administrative */}
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                    <h2 className="text-avtovo-text font-semibold mb-4">Szczegóły lotu</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                        <div className="space-y-3">
                            <p className="text-xs font-medium text-avtovo-text-secondary uppercase tracking-wide">Stan</p>
                            <Detail icon={<Key size={16} />} label="Kluczyk" value={vehicle.condition?.has_key == null ? '—' : (vehicle.condition.has_key ? 'Tak' : 'Nie')} />
                            <Detail icon={<Fuel size={16} />} label="Paliwo" value={vehicle.vehicle_specs?.fuel_type ?? '—'} />
                            <Detail icon={<Cog size={16} />} label="Napęd" value={vehicle.vehicle_specs?.drive_type ?? '—'} />
                        </div>
                        <div className="space-y-3">
                            <p className="text-xs font-medium text-avtovo-text-secondary uppercase tracking-wide">Cechy fizyczne</p>
                            <Detail icon={<Palette size={16} />} label="Kolor nadwozia" value={vehicle.vehicle_specs?.exterior_color ?? '—'} />
                            <Detail icon={<Cog size={16} />} label="Typ nadwozia" value={vehicle.vehicle_specs?.body_style ?? '—'} />
                            <Detail icon={<Cog size={16} />} label="Skrzynia biegów" value={vehicle.vehicle_specs?.transmission ?? '—'} />
                        </div>
                        <div className="space-y-3">
                            <p className="text-xs font-medium text-avtovo-text-secondary uppercase tracking-wide">Dane administracyjne</p>
                            <Detail icon={<MapPin size={16} />} label="Lokalizacja" value={`${vehicle.location?.display ?? '—'}${vehicle.location?.send_from ? ` (wysyłka z: ${vehicle.location.send_from})` : ''}`} />
                            <Detail icon={<ShieldCheck size={16} />} label="Sprzedawca" value={vehicle.seller?.name ? `${vehicle.seller.name}${vehicle.seller.type ? ` (${vehicle.seller.type})` : ''}` : '—'} />
                            <Detail icon={<FileCheck2 size={16} />} label="Platforma / dokument" value={[vehicle.platform, vehicle.sale_document?.name].filter(Boolean).join(' · ') || '—'} />
                        </div>
                    </div>
                </div>

                {/* Max affordable price calculator — only meaningful while the auction is still live */}
                {!isEnded && (
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4 flex items-center gap-2">
                            <TrendingUp size={18} className="text-avtovo-accent" /> Ile mogę zaoferować za auto?
                        </h2>
                        <p className="text-avtovo-text-secondary text-sm mb-3">
                            Podaj budżet i szacowany koszt naprawy — reszta (transport z Apibara, cło, akcyza, VAT) jest liczona automatycznie,
                            a wynikiem jest maksymalna cena, jaką możesz zapłacić za samo auto na aukcji.
                        </p>
                        <form onSubmit={handleCalculateMaxBid} className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-end">
                            <div>
                                <label className="text-xs text-avtovo-text-secondary">Twój budżet (PLN) *</label>
                                <input
                                    type="number"
                                    required
                                    value={budgetPln}
                                    onChange={e => setBudgetPln(e.target.value)}
                                    className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                />
                            </div>
                            <div>
                                <label className="text-xs text-avtovo-text-secondary">Szacowany koszt naprawy (PLN)</label>
                                <input
                                    type="number"
                                    value={repairCostPln}
                                    onChange={e => setRepairCostPln(e.target.value)}
                                    className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                />
                            </div>
                            <div>
                                <label className="text-xs text-avtovo-text-secondary">Pojemność silnika (cm³)</label>
                                <input
                                    type="number"
                                    value={engineCapacityCm3}
                                    onChange={e => setEngineCapacityCm3(e.target.value)}
                                    className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                />
                            </div>
                            <div>
                                <label className="text-xs text-avtovo-text-secondary">Rodzaj napędu</label>
                                <select
                                    value={fuelType}
                                    onChange={e => setFuelType(e.target.value as FuelType)}
                                    className="w-full mt-1 bg-avtovo-bg border border-avtovo-border text-avtovo-text rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                                >
                                    <option value="PETROL">Benzyna</option>
                                    <option value="DIESEL">Diesel</option>
                                    <option value="HYBRID">Hybryda (HEV/MHEV)</option>
                                    <option value="PLUGIN_HYBRID">Hybryda plug-in (PHEV)</option>
                                    <option value="ELECTRIC">Elektryczny</option>
                                </select>
                            </div>
                            <label className="flex items-center gap-2 text-sm text-avtovo-text-secondary">
                                <input type="checkbox" checked={suv} onChange={e => setSuv(e.target.checked)} />
                                To SUV (droższy transport morski)
                            </label>
                            <button
                                type="submit"
                                disabled={maxBidCalculating}
                                className="sm:col-span-2 flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover disabled:opacity-60 text-white rounded-lg py-2.5 text-sm font-medium transition-colors"
                            >
                                {maxBidCalculating && <Loader2 size={15} className="animate-spin" />} Policz maksymalną cenę
                            </button>
                        </form>
                        {maxBidError && <p className="text-red-400 text-sm mt-2">{maxBidError}</p>}
                        {maxBidResult && (
                            <div className="mt-4 bg-avtovo-bg border border-avtovo-border rounded-lg p-4 space-y-1.5 text-sm">
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>Transport (Apibara)</span>
                                    <span>{USD(maxBidResult.shippingCostUsd)} ({PLN(maxBidResult.shippingCostPln)})</span>
                                </div>
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>Transport morski / dostawa</span>
                                    <span>{USD(maxBidResult.shippingDeliveryUsd)} ({PLN(maxBidResult.shippingDeliveryPln)})</span>
                                </div>
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>Akcyza ({(maxBidResult.exciseRate * 100).toFixed(2)}%)</span>
                                    <span>{PLN(maxBidResult.excise)}</span>
                                </div>
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>Cło ({(maxBidResult.customsDutyRate * 100).toFixed(1)}%)</span>
                                    <span>{PLN(maxBidResult.customsDuty)}</span>
                                </div>
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>VAT ({(maxBidResult.vatRate * 100).toFixed(0)}%)</span>
                                    <span>{PLN(maxBidResult.vat)}</span>
                                </div>
                                <div className="flex items-center justify-between text-avtovo-text-secondary">
                                    <span>Naprawa</span>
                                    <span>{PLN(maxBidResult.estimatedRepairCostPln)}</span>
                                </div>
                                <div className="border-t border-avtovo-border my-2" />
                                {maxBidResult.budgetSufficient ? (
                                    <>
                                        <div className="flex items-center justify-between text-avtovo-text font-semibold">
                                            <span>Maks. cena za auto (PLN)</span>
                                            <span>{PLN(maxBidResult.maxCarPricePln)}</span>
                                        </div>
                                        <div className="flex items-center justify-between text-avtovo-text font-semibold">
                                            <span>Maks. cena za auto (USD)</span>
                                            <span>{USD(maxBidResult.maxCarPriceUsd)}</span>
                                        </div>
                                    </>
                                ) : (
                                    <p className="text-red-400 font-medium">Budżet nie wystarcza nawet na transport i naprawę tego auta.</p>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* Sale history */}
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-avtovo-text font-semibold flex items-center gap-2">
                            <Clock size={16} className="text-avtovo-accent" /> Historia sprzedaży
                        </h2>
                        {history === null && (
                            <button onClick={handleLoadHistory} disabled={loadingHistory} className="text-avtovo-accent text-sm hover:underline disabled:opacity-60">
                                {loadingHistory ? 'Ładowanie…' : 'Pokaż'}
                            </button>
                        )}
                    </div>
                    {history === null ? (
                        <p className="text-avtovo-text-secondary text-sm">Zapytanie do Apibara zużywa limit — pokaż tylko jeśli potrzebujesz.</p>
                    ) : history.length === 0 ? (
                        <p className="text-avtovo-text-secondary text-sm">Brak historii sprzedaży</p>
                    ) : (
                        <div className="space-y-2">
                            {history.map((entry, i) => {
                                const date = entry.sale_date ?? entry.auction_at;
                                const price = entry.price_usd ?? entry.current_bid_usd;
                                return (
                                    <div key={i} className="flex items-center justify-between text-sm bg-avtovo-bg border border-avtovo-border rounded-lg px-4 py-3">
                                        <span className="text-avtovo-text">
                                            {[entry.status, entry.platform].filter(Boolean).join(' · ') || '—'}
                                        </span>
                                        <span className="text-avtovo-text-secondary">{date ? String(date).slice(0, 10) : '—'}</span>
                                        <span className="text-avtovo-accent font-medium">{price != null ? USD(Number(price)) : '—'}</span>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Related vehicles */}
                <div>
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-avtovo-text font-semibold flex items-center gap-2">
                            <Gavel size={16} className="text-avtovo-accent" /> Podobne pojazdy
                        </h2>
                        {related === null && (
                            <button onClick={handleLoadRelated} disabled={loadingRelated} className="text-avtovo-accent text-sm hover:underline disabled:opacity-60">
                                {loadingRelated ? 'Ładowanie…' : 'Pokaż'}
                            </button>
                        )}
                    </div>
                    {related === null ? (
                        <p className="text-avtovo-text-secondary text-sm">Zapytanie do Apibara zużywa limit — pokaż tylko jeśli potrzebujesz.</p>
                    ) : related.length === 0 ? (
                        <p className="text-avtovo-text-secondary text-sm">Brak podobnych pojazdów</p>
                    ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                            {related.map((r, i) => {
                                // slug_vin looks like the right identifier for the single-vehicle
                                // endpoint but Apibara only actually resolves VIN or lot number by it
                                // (confirmed against the live API — slug_vin 404s there).
                                const id = r.vin ?? r.lot_number;
                                const price = r.pricing?.current_bid_usd ?? r.pricing?.last_sold_price_usd;
                                const title = r.title ?? [r.year, r.make, r.model].filter(Boolean).join(' ');
                                return (
                                    <button
                                        key={id ?? i}
                                        disabled={!id}
                                        onClick={() => id && navigate(`/aukcje/pojazd/${encodeURIComponent(id)}`)}
                                        className="text-left bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden transition-all duration-200 hover:border-gray-600 hover:shadow-lg hover:shadow-black/20 disabled:cursor-default"
                                    >
                                        <div className="aspect-[16/10] bg-avtovo-bg overflow-hidden">
                                            {r.media?.thumbs?.[0] ? (
                                                <img src={r.media.thumbs[0]} alt={title} className="w-full h-full object-cover" />
                                            ) : (
                                                <div className="w-full h-full flex items-center justify-center text-avtovo-border">
                                                    <Gavel size={28} />
                                                </div>
                                            )}
                                        </div>
                                        <div className="p-3">
                                            <p className="text-avtovo-text text-sm font-medium leading-tight line-clamp-2">{title || '—'}</p>
                                            <p className="text-avtovo-text-secondary text-xs mt-1">{r.location?.display ?? r.condition?.primary_damage ?? ''}</p>
                                            <p className="text-avtovo-accent font-semibold text-sm mt-1">{price != null ? USD(Number(price)) : '—'}</p>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

function KeyStat({ label, value, accent, icon }: { label: string; value: string; accent?: boolean; icon?: React.ReactNode }) {
    return (
        <div className="bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2.5">
            <p className="text-avtovo-text-secondary text-xs flex items-center gap-1">{icon}{label}</p>
            <p className={`font-semibold text-sm mt-0.5 ${accent ? 'text-avtovo-accent' : 'text-avtovo-text'}`}>{value}</p>
        </div>
    );
}

function Detail({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
    return (
        <div className="flex items-start gap-3">
            <span className="text-avtovo-accent mt-0.5">{icon}</span>
            <div>
                <p className="text-xs text-avtovo-text-secondary">{label}</p>
                <p className="text-avtovo-text font-medium text-sm">{value}</p>
            </div>
        </div>
    );
}
