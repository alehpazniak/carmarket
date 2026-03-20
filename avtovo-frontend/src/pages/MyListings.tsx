import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyCars } from '../api/cars';
import type {CarListing, Page} from '../types';
import CarCard from '../components/CarCard';
import { Plus, Car } from 'lucide-react';

export default function MyListings() {
    const [carsPage, setCarsPage] = useState<Page<CarListing> | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getMyCars().then(setCarsPage).catch(console.error).finally(() => setLoading(false));
    }, []);

    const cars = carsPage?.content ?? [];

    return (
        <div className="min-h-screen bg-avtovo-bg py-10">
            <div className="max-w-7xl mx-auto px-4">
                <div className="flex items-center justify-between mb-8">
                    <h1 className="text-2xl font-bold text-avtovo-text">Moje ogłoszenia</h1>
                    <Link to="/dodaj-ogloszenie"
                          className="flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2.5 rounded-xl text-sm font-medium transition-colors">
                        <Plus size={16} />
                        Dodaj ogłoszenie
                    </Link>
                </div>

                {loading ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        {Array.from({ length: 4 }).map((_, i) => (
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
                        <Car size={48} className="mx-auto text-avtovo-border mb-4" />
                        <p className="text-avtovo-text-secondary text-lg mb-6">Nie masz jeszcze żadnych ogłoszeń</p>
                        <Link to="/dodaj-ogloszenie"
                              className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-6 py-3 rounded-xl font-medium transition-colors">
                            <Plus size={16} />
                            Dodaj pierwsze ogłoszenie
                        </Link>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                        {cars.map(car => <CarCard key={car.id} car={car} />)}
                    </div>
                )}
            </div>
        </div>
    );
}