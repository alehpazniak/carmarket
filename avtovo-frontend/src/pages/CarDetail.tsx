import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { getCar, getMyCar, deleteCar } from '../api/cars';
import type {CarListing} from '../types';
import { useAuth } from '../context/AuthContext';
import { MapPin, Fuel, Gauge, Calendar, Cog, Palette, Tag, Trash2, ChevronLeft, ChevronRight, Check, Pencil, Zap, Car, Hash, Globe, ShieldCheck, Settings2, Sparkles, Flag, ShieldOff } from 'lucide-react';
import { ContactSellerButton } from '../components/ContactSellerButton';
import { ListingChatSection, type ListingChatSectionHandle } from '../components/ListingChatSection';
import { EQUIPMENT_CATEGORIES } from '../constants/equipment';
import { CATEGORY_LABELS } from '../constants/cars';
import { BODY_LABELS, CONDITION_LABELS, DRIVE_LABELS, FLAG_LABELS, type FlagField } from '../constants/technical';
import VideoPlayer from '../components/VideoPlayer';
import PromoteListingDialog from '../components/PromoteListingDialog';
import ReportListingDialog from '../components/ReportListingDialog';
import { activeUntilLabel } from '../utils/promotion';

const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny', HYBRID: 'Hybryda', LPG: 'LPG',
};

const TRANSMISSION_LABELS: Record<CarListing['transmission'], string> = {
    MANUAL: 'Manualna', AUTOMATIC: 'Automatyczna', SEMI_AUTOMATIC: 'Półautomatyczna',
};

export default function CarDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { user, isAuthenticated } = useAuth();
    const [car, setCar] = useState<CarListing | null>(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<'notFound' | 'failed' | null>(null);
    const [imgIdx, setImgIdx] = useState(0);
    const [promoteOpen, setPromoteOpen] = useState(false);
    const [reportOpen, setReportOpen] = useState(false);
    const chatRef = useRef<ListingChatSectionHandle>(null);

    // `loading` starts true, so the first load doesn't set it; retry() does
    const loadCar = useCallback(() => {
        if (!id) return;
        getCar(id)
            .then(setCar)
            .catch(async (err) => {
                const notFound = axios.isAxiosError(err) && err.response?.status === 404;
                // A blocked or suspended listing is 404 publicly; its owner still sees it (with the reason)
                if (notFound && isAuthenticated) {
                    try {
                        setCar(await getMyCar(id));
                        return;
                    } catch { /* not the owner: fall through to "not found" */ }
                }
                if (!notFound) console.error(err);
                setLoadError(notFound ? 'notFound' : 'failed');
            })
            .finally(() => setLoading(false));
    }, [id, isAuthenticated]);

    useEffect(() => {
        loadCar();
    }, [loadCar]);

    const retry = () => {
        setLoading(true);
        setLoadError(null);
        loadCar();
    };

    const handleDelete = async () => {
        if (!car || !confirm('Czy na pewno chcesz usunąć to ogłoszenie?')) return;
        await deleteCar(car.id);
        navigate('/moje-ogloszenia');
    };

    if (loading) return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <div className="w-8 h-8 border-2 border-avtovo-accent border-t-transparent rounded-full animate-spin" />
        </div>
    );

    if (!car) return (
        <div className="min-h-screen bg-avtovo-bg flex flex-col items-center justify-center gap-4 px-4 text-center">
            {loadError === 'failed' ? (
                <>
                    <p className="text-avtovo-text-secondary">Nie udało się wczytać ogłoszenia</p>
                    <button onClick={retry}
                            className="bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium transition-colors">
                        Spróbuj ponownie
                    </button>
                </>
            ) : (
                <>
                    <p className="text-avtovo-text-secondary">To ogłoszenie zostało usunięte lub nie istnieje</p>
                    <Link to="/" className="text-avtovo-accent hover:underline text-sm">Przejdź na stronę główną</Link>
                </>
            )}
        </div>
    );

    const images = car.imageUrls || [];
    const isOwner = user?.id === car.sellerId;
    const selectedEquipment = new Set(car.equipment ?? []);
    const equipmentByCategory = EQUIPMENT_CATEGORIES
        .map(cat => ({...cat, options: cat.options.filter(o => selectedEquipment.has(o.code))}))
        .filter(cat => cat.options.length > 0);
    const tech = car.technicalData ?? {};
    const flags = (Object.keys(FLAG_LABELS) as FlagField[]).filter(f => tech[f]);
    const specs = [
        { icon: <Tag size={16} />, label: 'Kategoria', value: CATEGORY_LABELS[car.category ?? 'PASSENGER'] },
        { icon: <ShieldCheck size={16} />, label: 'Stan', value: tech.condition && CONDITION_LABELS[tech.condition] },
        { icon: <Car size={16} />, label: 'Wersja', value: tech.version },
        { icon: <Calendar size={16} />, label: 'Rok produkcji', value: car.year },
        { icon: <Gauge size={16} />, label: 'Przebieg', value: `${car.mileage.toLocaleString('pl-PL')} km` },
        { icon: <Fuel size={16} />, label: 'Paliwo', value: FUEL_LABELS[car.fuelType] || car.fuelType },
        { icon: <Settings2 size={16} />, label: 'Pojemność skokowa', value: tech.engineCapacity != null && `${tech.engineCapacity.toLocaleString('pl-PL')} cm³` },
        { icon: <Zap size={16} />, label: 'Moc', value: tech.powerHp != null && `${tech.powerHp} KM` },
        { icon: <Cog size={16} />, label: 'Skrzynia', value: TRANSMISSION_LABELS[car.transmission] ?? car.transmission },
        { icon: <Cog size={16} />, label: 'Napęd', value: tech.driveType && DRIVE_LABELS[tech.driveType] },
        { icon: <Car size={16} />, label: 'Typ nadwozia', value: tech.bodyType && BODY_LABELS[tech.bodyType] },
        { icon: <Car size={16} />, label: 'Liczba drzwi', value: tech.doors },
        { icon: <Car size={16} />, label: 'Liczba miejsc', value: tech.seats },
        { icon: <Palette size={16} />, label: 'Kolor', value: car.color || '—' },
        { icon: <Hash size={16} />, label: 'VIN', value: tech.vin },
        { icon: <Globe size={16} />, label: 'Kraj pochodzenia', value: tech.originCountry },
        { icon: <Calendar size={16} />, label: 'Pierwsza rejestracja', value: tech.firstRegistrationDate && new Date(tech.firstRegistrationDate).toLocaleDateString('pl-PL') },
        { icon: <MapPin size={16} />, label: 'Lokalizacja', value: `${car.city}, ${car.country}` },
    ].filter(s => s.value !== null && s.value !== undefined && s.value !== false && s.value !== '');

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-5xl mx-auto px-4">
                <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text mb-6 transition-colors">
                    <ChevronLeft size={18} />
                    Wróć
                </button>

                {(car.status === 'BLOCKED' || car.status === 'SUSPENDED') && (
                    <div className="mb-6 flex items-start gap-3 bg-red-500/10 border border-red-700 rounded-xl p-4 text-sm">
                        <ShieldOff size={18} className="text-red-400 shrink-0 mt-0.5" />
                        <div>
                            <p className="font-medium text-red-300">
                                {car.status === 'BLOCKED' ? 'Ogłoszenie zostało zablokowane przez moderatora' : 'Ogłoszenie jest zawieszone'}
                            </p>
                            <p className="text-avtovo-text-secondary mt-1">
                                {car.status === 'BLOCKED'
                                    ? `Nie jest widoczne dla innych użytkowników.${car.moderationReason ? ` Powód: ${car.moderationReason}` : ''}`
                                    : 'Twoje konto jest zablokowane, więc ogłoszenie nie jest widoczne dla innych użytkowników.'}
                            </p>
                        </div>
                    </div>
                )}

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Left: images + details */}
                    <div className="lg:col-span-2 space-y-4">
                        {/* Gallery */}
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden">
                            <div className="relative aspect-[16/10] bg-avtovo-bg">
                                {images.length > 0 ? (
                                    <>
                                        <img src={images[imgIdx]} alt={`${car.make} ${car.model}`} className="w-full h-full object-cover" />
                                        {images.length > 1 && (
                                            <>
                                                <button onClick={() => setImgIdx(i => (i - 1 + images.length) % images.length)}
                                                        className="absolute left-3 top-1/2 -translate-y-1/2 bg-black/60 hover:bg-black/80 rounded-full p-2 transition-colors">
                                                    <ChevronLeft size={18} className="text-white" />
                                                </button>
                                                <button onClick={() => setImgIdx(i => (i + 1) % images.length)}
                                                        className="absolute right-3 top-1/2 -translate-y-1/2 bg-black/60 hover:bg-black/80 rounded-full p-2 transition-colors">
                                                    <ChevronRight size={18} className="text-white" />
                                                </button>
                                                <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-1">
                                                    {images.map((_, i) => (
                                                        <button key={i} onClick={() => setImgIdx(i)}
                                                                className={`w-2 h-2 rounded-full transition-colors ${i === imgIdx ? 'bg-white' : 'bg-white/40'}`} />
                                                    ))}
                                                </div>
                                            </>
                                        )}
                                    </>
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center text-avtovo-border">
                                        <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="0.8">
                                            <path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v3" />
                                            <rect x="9" y="11" width="14" height="10" rx="2" />
                                            <circle cx="12" cy="16" r="1" />
                                            <circle cx="20" cy="16" r="1" />
                                        </svg>
                                    </div>
                                )}
                            </div>
                            {images.length > 1 && (
                                <div className="flex gap-2 p-3 overflow-x-auto">
                                    {images.map((src, i) => (
                                        <button key={i} onClick={() => setImgIdx(i)}
                                                className={`w-16 h-12 rounded-lg overflow-hidden flex-shrink-0 border-2 transition-colors ${i === imgIdx ? 'border-avtovo-accent' : 'border-transparent'}`}>
                                            <img src={src} alt="" className="w-full h-full object-cover" />
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Specs */}
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                            <h2 className="text-avtovo-text font-semibold mb-4">Dane techniczne</h2>
                            <div className="grid grid-cols-2 gap-4">
                                {specs.map(({ icon, label, value }) => (
                                    <div key={label} className="flex items-start gap-3">
                                        <span className="text-avtovo-accent mt-0.5">{icon}</span>
                                        <div>
                                            <p className="text-xs text-avtovo-text-secondary">{label}</p>
                                            <p className="text-avtovo-text font-medium">{value}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                            {flags.length > 0 && (
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5 mt-5 pt-4 border-t border-avtovo-border">
                                    {flags.map(f => (
                                        <div key={f} className="flex items-center gap-2 text-sm text-avtovo-text">
                                            <Check size={14} className="text-avtovo-accent flex-shrink-0" />
                                            {FLAG_LABELS[f]}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Video */}
                        {car.videoUrl && (
                            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                                <h2 className="text-avtovo-text font-semibold mb-4">Film</h2>
                                <VideoPlayer url={car.videoUrl} title={`${car.make} ${car.model} – film`} />
                            </div>
                        )}

                        {/* Equipment */}
                        {equipmentByCategory.length > 0 && (
                            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                                <h2 className="text-avtovo-text font-semibold mb-4">Wyposażenie</h2>
                                <div className="space-y-4">
                                    {equipmentByCategory.map(cat => (
                                        <div key={cat.key}>
                                            <h3 className="text-sm font-medium text-avtovo-text-secondary mb-2">{cat.label}</h3>
                                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
                                                {cat.options.map(opt => (
                                                    <div key={opt.code} className="flex items-center gap-2 text-sm text-avtovo-text">
                                                        <Check size={14} className="text-avtovo-accent flex-shrink-0" />
                                                        {opt.label}
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Description */}
                        {car.description && (
                            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                                <h2 className="text-avtovo-text font-semibold mb-3">Opis</h2>
                                <p className="text-avtovo-text-secondary whitespace-pre-line leading-relaxed">{car.description}</p>
                            </div>
                        )}
                    </div>

                    {/* Right: price + actions. Card and contact buttons stick together, so the
                        card never slides over the buttons. */}
                    <div>
                        <div className="space-y-4 lg:sticky lg:top-20">
                            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                                <h1 className="text-2xl font-bold text-avtovo-text mb-1">
                                    {car.make} {car.model}
                                </h1>
                                <p className="text-avtovo-text-secondary text-sm mb-4">{car.year} · {car.city}</p>
                                <p className="text-3xl font-bold text-avtovo-accent mb-6">
                                    {car.price.toLocaleString('pl-PL')} zł
                                </p>

                                {isOwner ? (
                                    <div className="space-y-2">
                                        <Link to={`/ogloszenia/${car.id}/edytuj`}
                                              className="w-full flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white py-3 rounded-xl font-medium transition-colors">
                                            <Pencil size={16} />
                                            Edytuj ogłoszenie
                                        </Link>
                                        {car.status === 'ACTIVE' && (
                                            <>
                                                <button onClick={() => setPromoteOpen(true)}
                                                        className="w-full flex items-center justify-center gap-2 border border-avtovo-accent/60 text-avtovo-accent hover:bg-avtovo-accent/10 py-3 rounded-xl font-medium transition-colors">
                                                    <Sparkles size={16} />
                                                    Promuj ogłoszenie
                                                </button>
                                                {activeUntilLabel(car.promotedUntil) && (
                                                    <p className="text-center text-xs text-avtovo-text-secondary">
                                                        Promowane do {activeUntilLabel(car.promotedUntil)}
                                                    </p>
                                                )}
                                            </>
                                        )}
                                        <button onClick={handleDelete}
                                                className="w-full flex items-center justify-center gap-2 border border-red-500/50 text-red-400 hover:bg-red-500/10 py-3 rounded-xl transition-colors">
                                            <Trash2 size={16} />
                                            Usuń ogłoszenie
                                        </button>
                                    </div>
                                ) : null}
                            </div>
                            <ContactSellerButton carId={car.id} sellerId={car.sellerId}
                                                 listingActive={car.status === 'ACTIVE'} onWrite={() => chatRef.current?.openAndFocus()} />
                            {isAuthenticated && !isOwner && (car.status === 'ACTIVE' || car.status === 'SOLD') && (
                                <button onClick={() => setReportOpen(true)}
                                        className="w-full flex items-center justify-center gap-2 text-sm text-avtovo-text-secondary hover:text-red-400 py-2 transition-colors">
                                    <Flag size={14} /> Zgłoś ogłoszenie
                                </button>
                            )}
                        </div>
                    </div>
                </div>

                <ListingChatSection ref={chatRef} carId={car.id} sellerId={car.sellerId} />
            </div>
            {promoteOpen && isOwner && <PromoteListingDialog car={car} onClose={() => setPromoteOpen(false)} />}
            {reportOpen && <ReportListingDialog carId={car.id} onClose={() => setReportOpen(false)} />}
        </div>
    );
}