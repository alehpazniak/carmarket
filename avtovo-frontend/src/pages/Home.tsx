import { useEffect, useState } from 'react';
import { searchCars } from '../api/cars';
import type { CarDocument } from '../types';
import CarCard from '../components/CarCard';
import { useAuth } from '../context/AuthContext';
import { Search } from 'lucide-react';
export default function Home() {
    const [cars, setCars] = useState<CarDocument[]>([]);
    const [loading, setLoading] = useState(true);
    const [query, setQuery] = useState('');
    const [make, setMake] = useState('');
    const [priceFrom, setPriceFrom] = useState('');
    const [priceTo, setPriceTo] = useState('');
    const [fuelType, setFuelType] = useState('');
    const { loginWithGoogle, isAuthenticated } = useAuth();
    const fetchCars = (params = {}) => {
        setLoading(true);
        searchCars(params)
            .then(setCars)
            .catch(console.error)
            .finally(() => setLoading(false));
    };
    useEffect(() => {
        searchCars()
            .then(setCars)
            .catch(console.error)
            .finally(() => setLoading(false));
    }, []);
    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchCars({
            ...(query && { query }),
            ...(make && { make }),
            ...(priceFrom && { priceFrom }),
            ...(priceTo && { priceTo }),
            ...(fuelType && { fuelType }),
        });
    };
    const MAKES = ['Audi', 'BMW', 'Ford', 'Honda', 'Hyundai', 'Kia', 'Mazda',
        'Mercedes-Benz', 'Nissan', 'Opel', 'Peugeot', 'Renault',
        'Skoda', 'Toyota', 'Volkswagen', 'Volvo'];
    const FUEL_LABELS: Record<string, string> = {
        PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny',
        HYBRID: 'Hybryda', LPG: 'LPG',
    };
    return (
        <div className="min-h-screen bg-avtovo-bg">
            {/* Hero */}
            <div className="bg-gradient-to-b from-avtovo-card to-avtovo-bg border-b border-avtovo-border">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 text-center">
                    <h1 className="text-4xl sm:text-5xl font-bold text-avtovo-text mb-4">
                        Znajdź swoje <span className="text-avtovo-accent">wymarzone auto</span>
                    </h1>
                    <p className="text-avtovo-text-secondary text-lg mb-8">
                        Tysiące ogłoszeń motoryzacyjnych w jednym miejscu
                    </p>
                    {/* Search form */}
                    <form onSubmit={handleSearch} className="max-w-4xl mx-auto space-y-3">
                        {/* Main search bar */}
                        <div className="relative">
                            <Search size={20} className="absolute left-4 top-1/2 -translate-y-1/2 text-avtovo-muted" />
                            <input
                                type="text"
                                placeholder="Szukaj: marka, model, opis..."
                                value={query}
                                onChange={e => setQuery(e.target.value)}
                                className="w-full bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl pl-12 pr-4 py-4 text-base focus:outline-none focus:border-avtovo-accent transition-colors"
                            />
                        </div>
                        {/* Filters row */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                            <select
                                value={make}
                                onChange={e => setMake(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            >
                                <option value="">Wszystkie marki</option>
                                {MAKES.map(m => <option key={m} value={m}>{m}</option>)}
                            </select>
                            <select
                                value={fuelType}
                                onChange={e => setFuelType(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            >
                                <option value="">Wszystkie paliwa</option>
                                {Object.entries(FUEL_LABELS).map(([v, l]) => (
                                    <option key={v} value={v}>{l}</option>
                                ))}
                            </select>
                            <input
                                type="number"
                                placeholder="Cena od (zł)"
                                value={priceFrom}
                                onChange={e => setPriceFrom(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Cena do (zł)"
                                value={priceTo}
                                onChange={e => setPriceTo(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                        </div>
                        <button
                            type="submit"
                            className="w-full sm:w-auto bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-10 py-3 rounded-xl font-semibold transition-colors"
                        >
                            Szukaj
                        </button>
                    </form>
                    {!isAuthenticated && (
                        <div className="mt-6">
                            <button
                                onClick={loginWithGoogle}
                                className="inline-flex items-center gap-3 bg-white text-gray-900 px-6 py-3 rounded-xl font-medium hover:bg-gray-100 transition-colors"
                            >
                                <svg width="20" height="20" viewBox="0 0 24 24">
                                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.47 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                                </svg>
                                Zaloguj się przez Google
                            </button>
                        </div>
                    )}
                </div>
            </div>
            {/* Results */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
                {loading ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        {Array.from({ length: 8 }).map((_, i) => (
                            <div key={i} className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden animate-pulse">
                                <div className="aspect-[16/10] bg-avtovo-border" />
                                <div className="p-4 space-y-2">
                                    <div className="h-4 bg-avtovo-border rounded w-3/4" />
                                    <div className="h-4 bg-avtovo-border rounded w-1/2" />
                                </div>
                            </div>
                        ))}
                    </div>
                ) : cars.length === 0 ? (
                    <div className="text-center py-20">
                        <p className="text-avtovo-text-secondary text-lg">Brak ogłoszeń</p>
                    </div>
                ) : (
                    <>
                        <p className="text-avtovo-text-secondary mb-6">{cars.length} ogłoszeń</p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                            {cars.map(car => (
                                <CarCard key={car.id} car={car} />
                            ))}
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}