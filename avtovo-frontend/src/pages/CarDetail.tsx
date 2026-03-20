import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getCar, deleteCar } from '../api/cars';
import type {CarListing} from '../types';
import { useAuth } from '../context/AuthContext';
import { MapPin, Fuel, Gauge, Calendar, Cog, Palette, Trash2, ChevronLeft, ChevronRight } from 'lucide-react';

const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny', HYBRID: 'Hybryda', LPG: 'LPG',
};

export default function CarDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [car, setCar] = useState<CarListing | null>(null);
    const [loading, setLoading] = useState(true);
    const [imgIdx, setImgIdx] = useState(0);

    useEffect(() => {
        if (id) getCar(id).then(setCar).catch(console.error).finally(() => setLoading(false));
    }, [id]);

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
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center">
            <p className="text-avtovo-text-secondary">Ogłoszenie nie zostało znalezione</p>
        </div>
    );

    const images = car.imageUrls || [];
    const isOwner = user?.id === car.sellerId;

    return (
        <div className="min-h-screen bg-avtovo-bg py-8">
            <div className="max-w-5xl mx-auto px-4">
                <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text mb-6 transition-colors">
                    <ChevronLeft size={18} />
                    Wróć
                </button>

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
                                {[
                                    { icon: <Calendar size={16} />, label: 'Rok produkcji', value: car.year },
                                    { icon: <Gauge size={16} />, label: 'Przebieg', value: `${car.mileage.toLocaleString('pl-PL')} km` },
                                    { icon: <Fuel size={16} />, label: 'Paliwo', value: FUEL_LABELS[car.fuelType] || car.fuelType },
                                    { icon: <Cog size={16} />, label: 'Skrzynia', value: car.transmission === 'MANUAL' ? 'Manualna' : 'Automatyczna' },
                                    { icon: <Palette size={16} />, label: 'Kolor', value: car.color || '—' },
                                    { icon: <MapPin size={16} />, label: 'Lokalizacja', value: `${car.city}, ${car.country}` },
                                ].map(({ icon, label, value }) => (
                                    <div key={label} className="flex items-start gap-3">
                                        <span className="text-avtovo-accent mt-0.5">{icon}</span>
                                        <div>
                                            <p className="text-xs text-avtovo-text-secondary">{label}</p>
                                            <p className="text-avtovo-text font-medium">{value}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Description */}
                        {car.description && (
                            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6">
                                <h2 className="text-avtovo-text font-semibold mb-3">Opis</h2>
                                <p className="text-avtovo-text-secondary whitespace-pre-line leading-relaxed">{car.description}</p>
                            </div>
                        )}
                    </div>

                    {/* Right: price + actions */}
                    <div className="space-y-4">
                        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 sticky top-20">
                            <h1 className="text-2xl font-bold text-avtovo-text mb-1">
                                {car.make} {car.model}
                            </h1>
                            <p className="text-avtovo-text-secondary text-sm mb-4">{car.year} · {car.city}</p>
                            <p className="text-3xl font-bold text-avtovo-accent mb-6">
                                {car.price.toLocaleString('pl-PL')} zł
                            </p>

                            {isOwner ? (
                                <button onClick={handleDelete}
                                        className="w-full flex items-center justify-center gap-2 border border-red-500/50 text-red-400 hover:bg-red-500/10 py-3 rounded-xl transition-colors">
                                    <Trash2 size={16} />
                                    Usuń ogłoszenie
                                </button>
                            ) : (
                                <div className="bg-avtovo-bg border border-avtovo-border rounded-xl p-4 text-center">
                                    <p className="text-avtovo-text-secondary text-sm">Skontaktuj się ze sprzedającym</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}