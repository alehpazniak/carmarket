import { useEffect, useState } from 'react';
import { searchCars } from '../api/cars';
import type { CarDocument } from '../types';
import CarCard from '../components/CarCard';
import { useAuth } from '../context/AuthContext';
import { Search, SlidersHorizontal, ChevronDown, Car, Zap, Truck, Construction, Van, Motorbike, Caravan, Tractor, type LucideIcon } from 'lucide-react';
import { CATEGORY_LABELS, hasModelList, makesFor, modelsFor } from '../constants/cars';
import type { VehicleCategory } from '../types';

/** Category tabs like on otomoto.pl. "Elektryczne" is not a separate category — it's passenger cars with fuel = ELECTRIC. */
type CategoryTab = { id: string; label: string; icon: LucideIcon; category: VehicleCategory; fuelType?: string };
const CATEGORY_TABS: CategoryTab[] = [
    { id: 'PASSENGER', label: CATEGORY_LABELS.PASSENGER, icon: Car, category: 'PASSENGER' },
    { id: 'ELECTRIC', label: 'Elektryczne', icon: Zap, category: 'PASSENGER', fuelType: 'ELECTRIC' },
    { id: 'TRUCK', label: CATEGORY_LABELS.TRUCK, icon: Truck, category: 'TRUCK' },
    { id: 'CONSTRUCTION', label: CATEGORY_LABELS.CONSTRUCTION, icon: Construction, category: 'CONSTRUCTION' },
    { id: 'VAN', label: CATEGORY_LABELS.VAN, icon: Van, category: 'VAN' },
    { id: 'MOTORCYCLE', label: CATEGORY_LABELS.MOTORCYCLE, icon: Motorbike, category: 'MOTORCYCLE' },
    { id: 'TRAILER', label: CATEGORY_LABELS.TRAILER, icon: Caravan, category: 'TRAILER' },
    { id: 'AGRICULTURAL', label: CATEGORY_LABELS.AGRICULTURAL, icon: Tractor, category: 'AGRICULTURAL' },
];
export default function Home() {
    const [cars, setCars] = useState<CarDocument[]>([]);
    const [loading, setLoading] = useState(true);
    const [tab, setTab] = useState<CategoryTab>(CATEGORY_TABS[0]);
    const [query, setQuery] = useState('');
    const [make, setMake] = useState('');
    const [model, setModel] = useState('');
    const [transmission, setTransmission] = useState('');
    const [priceFrom, setPriceFrom] = useState('');
    const [priceTo, setPriceTo] = useState('');
    const [mileageFrom, setMileageFrom] = useState('');
    const [mileageTo, setMileageTo] = useState('');
    const [fuelType, setFuelType] = useState('');
    const [yearFrom, setYearFrom] = useState('');
    const [yearTo, setYearTo] = useState('');
    const [city, setCity] = useState('');
    const [sortOption, setSortOption] = useState('createdAt,desc');
    const [filtersOpen, setFiltersOpen] = useState(true);
    const { loginWithGoogle, isAuthenticated } = useAuth();
    const fetchCars = (params = {}) => {
        setLoading(true);
        searchCars(params)
            .then(setCars)
            .catch(console.error)
            .finally(() => setLoading(false));
    };
    const buildParams = (sortOverride = sortOption, tabOverride = tab, fuelOverride = fuelType): Record<string, string> => ({
        category: tabOverride.category,
        ...(query && { query }),
        ...(make && { make }),
        ...(model && { model }),
        ...(transmission && { transmission }),
        ...(priceFrom && { priceFrom }),
        ...(priceTo && { priceTo }),
        ...(mileageFrom && { mileageMin: mileageFrom }),
        ...(mileageTo && { mileageMax: mileageTo }),
        ...(fuelOverride && { fuelType: fuelOverride }),
        ...(yearFrom && { yearFrom }),
        ...(yearTo && { yearTo }),
        ...(city && { city }),
        ...(sortOverride && { sort: sortOverride }),
    });
    useEffect(() => {
        fetchCars(buildParams());
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchCars(buildParams());
        setFiltersOpen(false);
    };
    const handleSortChange = (value: string) => {
        setSortOption(value);
        fetchCars(buildParams(value));
    };
    const handleTabChange = (next: CategoryTab) => {
        if (next.id === tab.id) return;
        // Entering "Elektryczne" forces fuel = ELECTRIC; leaving it drops that forced filter.
        const nextFuel = next.fuelType ?? (tab.fuelType ? '' : fuelType);
        setTab(next);
        setFuelType(nextFuel);
        const params = buildParams(sortOption, next, nextFuel);
        // Each category has its own makes: drop a make (and its model) the new category doesn't have
        if (next.category !== tab.category && make && !makesFor(next.category).includes(make)) {
            setMake('');
            setModel('');
            delete params.make;
            delete params.model;
        }
        fetchCars(params);
    };
    const makes = makesFor(tab.category);
    const modelsForMake = modelsFor(tab.category, make);
    const FUEL_LABELS: Record<string, string> = {
        PETROL: 'Benzyna', DIESEL: 'Diesel', ELECTRIC: 'Elektryczny',
        HYBRID: 'Hybryda', LPG: 'LPG',
    };
    const TRANSMISSION_LABELS: Record<string, string> = {
        MANUAL: 'Manualna', AUTOMATIC: 'Automatyczna',
    };
    const SORT_OPTIONS: { value: string; label: string }[] = [
        { value: 'createdAt,desc', label: 'Najnowsze' },
        { value: 'price,asc', label: 'Cena: od najniższej' },
        { value: 'price,desc', label: 'Cena: od najwyższej' },
        { value: 'mileage,asc', label: 'Przebieg: od najmniejszego' },
        { value: 'year,desc', label: 'Rok: najnowszy' },
    ];
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
                    {/* Category tabs */}
                    <div className="flex gap-1 overflow-x-auto pb-2 mb-6 sm:justify-center [scrollbar-width:none]">
                        {CATEGORY_TABS.map(t => {
                            const active = t.id === tab.id;
                            const Icon = t.icon;
                            return (
                                <button
                                    key={t.id}
                                    type="button"
                                    onClick={() => handleTabChange(t)}
                                    aria-pressed={active}
                                    className={`flex items-center gap-2 shrink-0 rounded-full px-5 py-2.5 text-sm sm:text-base transition-colors border ${active
                                        ? 'border-avtovo-accent bg-avtovo-accent/10 text-avtovo-accent font-semibold'
                                        : 'border-transparent text-avtovo-text-secondary hover:text-avtovo-text'}`}
                                >
                                    <Icon size={active ? 22 : 18} />
                                    {t.label}
                                </button>
                            );
                        })}
                    </div>
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
                        {/* Filters toggle */}
                        <button
                            type="button"
                            onClick={() => setFiltersOpen(prev => !prev)}
                            className="flex items-center gap-2 mx-auto text-sm text-avtovo-text-secondary hover:text-avtovo-text transition-colors"
                        >
                            <SlidersHorizontal size={14} />
                            {filtersOpen ? 'Ukryj filtry' : 'Pokaż filtry'}
                            <ChevronDown size={14} className={`transition-transform duration-300 ${filtersOpen ? 'rotate-180' : ''}`} />
                        </button>
                        {/* Filters row */}
                        <div
                            className={`grid grid-cols-2 sm:grid-cols-4 gap-3 overflow-hidden transition-all duration-300 ease-in-out ${filtersOpen ? 'max-h-[500px] opacity-100' : 'max-h-0 opacity-0'}`}
                        >
                            <select
                                value={make}
                                onChange={e => { setMake(e.target.value); setModel(''); }}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            >
                                <option value="">Wszystkie marki</option>
                                {makes.map(m => <option key={m} value={m}>{m}</option>)}
                            </select>
                            {hasModelList(tab.category) ? (
                                <select
                                    value={model}
                                    onChange={e => setModel(e.target.value)}
                                    disabled={modelsForMake.length === 0}
                                    className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <option value="">{modelsForMake.length === 0 ? 'Najpierw wybierz markę' : 'Wszystkie modele'}</option>
                                    {modelsForMake.map(m => <option key={m} value={m}>{m}</option>)}
                                </select>
                            ) : (
                                <input
                                    type="text"
                                    placeholder="Model"
                                    value={model}
                                    onChange={e => setModel(e.target.value)}
                                    className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                                />
                            )}
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
                            <select
                                value={transmission}
                                onChange={e => setTransmission(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            >
                                <option value="">Wszystkie skrzynie</option>
                                {Object.entries(TRANSMISSION_LABELS).map(([v, l]) => (
                                    <option key={v} value={v}>{l}</option>
                                ))}
                            </select>
                            <input
                                type="number"
                                placeholder="Cena od (zł)"
                                value={priceFrom}
                                onChange={e => setPriceFrom(e.target.value)}
                                step={100} min={0}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Cena do (zł)"
                                value={priceTo}
                                onChange={e => setPriceTo(e.target.value)}
                                step={100} min={0}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Przebieg od (km)"
                                value={mileageFrom}
                                onChange={e => setMileageFrom(e.target.value)}
                                step={100} min={0}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Przebieg do (km)"
                                value={mileageTo}
                                onChange={e => setMileageTo(e.target.value)}
                                step={100} min={0}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Rok od"
                                value={yearFrom}
                                onChange={e => setYearFrom(e.target.value)}
                                min={1900} max={new Date().getFullYear() + 1}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="number"
                                placeholder="Rok do"
                                value={yearTo}
                                onChange={e => setYearTo(e.target.value)}
                                min={1900} max={new Date().getFullYear() + 1}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text placeholder-avtovo-muted rounded-xl px-3 py-3 focus:outline-none focus:border-avtovo-accent"
                            />
                            <input
                                type="text"
                                placeholder="Lokalizacja (miasto)"
                                value={city}
                                onChange={e => setCity(e.target.value)}
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
                        <div className="flex items-center justify-between mb-6 gap-3 flex-wrap">
                            <p className="text-avtovo-text-secondary">{cars.length} ogłoszeń</p>
                            <select
                                value={sortOption}
                                onChange={e => handleSortChange(e.target.value)}
                                className="bg-avtovo-card border border-avtovo-border text-avtovo-text rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-avtovo-accent"
                            >
                                {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                            </select>
                        </div>
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