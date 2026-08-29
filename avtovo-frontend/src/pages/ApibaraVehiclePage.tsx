import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getApibaraVehicle, getVehicleHistory, getRelatedVehicles } from '../api/auctions';
import type { ApibaraVehicleDetail, ApibaraHistoryEntry, ApibaraRelatedVehicle } from '../types/auctions';
import PhotoGallery, { type GalleryPhoto } from '../components/PhotoGallery';
import { useAuth } from '../context/AuthContext';
import {
    ChevronLeft, MapPin, Key, ShieldCheck, Fuel, Cog, Palette, FileCheck2,
    Lock, Clock, Gavel, Copy, Check, Gauge, Timer,
} from 'lucide-react';

type Vehicle = NonNullable<ApibaraVehicleDetail['data']>;

const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;

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
    const [history, setHistory] = useState<ApibaraHistoryEntry[]>([]);
    const [related, setRelated] = useState<ApibaraRelatedVehicle[]>([]);
    const [vinCopied, setVinCopied] = useState(false);

    useEffect(() => {
        // Apibara live-lookup requires a logged-in user (see gateway/auction-import-service
        // security config) — don't fire the request anonymously, it would just 401.
        if (!identifier || authLoading || !isAuthenticated) return;
        getApibaraVehicle(identifier)
            .then(result => {
                if (!result.data) setError('Nie znaleziono pojazdu');
                setVehicle(result.data);
            })
            .catch(() => setError('Nie udało się pobrać danych pojazdu (limit Apibara lub błędne dane)'))
            .finally(() => setLoading(false));

        // Best-effort extras — a missing/empty history or related list just hides that section.
        getVehicleHistory(identifier)
            .then(result => setHistory(Array.isArray(result.data) ? result.data : result.data?.items ?? []))
            .catch(() => setHistory([]));
        getRelatedVehicles(identifier)
            .then(result => setRelated([
                ...(result.data?.source ?? []),
                ...(result.data?.upcoming ?? []),
                ...(result.data?.past ?? []),
            ].slice(0, 8)))
            .catch(() => setRelated([]));
    }, [identifier, authLoading, isAuthenticated]);

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

                {/* Sale history */}
                {history.length > 0 && (
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                        <h2 className="text-avtovo-text font-semibold mb-4 flex items-center gap-2">
                            <Clock size={16} className="text-avtovo-accent" /> Historia sprzedaży
                        </h2>
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
                    </div>
                )}

                {/* Related vehicles */}
                {related.length > 0 && (
                    <div>
                        <h2 className="text-avtovo-text font-semibold mb-4 flex items-center gap-2">
                            <Gavel size={16} className="text-avtovo-accent" /> Podobne pojazdy
                        </h2>
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                            {related.map((r, i) => {
                                const id = r.slug_vin ?? r.vin ?? r.lot_number;
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
                    </div>
                )}
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
