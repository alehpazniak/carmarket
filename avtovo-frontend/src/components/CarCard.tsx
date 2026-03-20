import { Link } from 'react-router-dom';
import type {CarDocument, CarListing} from '../types';
import { MapPin, Fuel, Gauge, Calendar } from 'lucide-react';

interface Props {
    car: CarListing | CarDocument;
}

const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna',
    DIESEL: 'Diesel',
    ELECTRIC: 'Elektryczny',
    HYBRID: 'Hybryda',
    LPG: 'LPG',
};


export default function CarCard({ car }: Props) {
    const mainImage = car.imageUrls?.[0];

    return (
        <Link to={`/ogloszenia/${car.id}`} className="group block">
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden hover:border-gray-600 transition-all duration-200 hover:shadow-lg hover:shadow-black/20">
                {/* Image */}
                <div className="aspect-[16/10] bg-avtovo-bg overflow-hidden">
                    {mainImage ? (
                        <img
                            src={mainImage}
                            alt={`${car.make} ${car.model}`}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                        />
                    ) : (
                        <div className="w-full h-full flex items-center justify-center">
                            <div className="text-avtovo-border">
                                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
                                    <path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v3" />
                                    <rect x="9" y="11" width="14" height="10" rx="2" />
                                    <circle cx="12" cy="16" r="1" />
                                    <circle cx="20" cy="16" r="1" />
                                </svg>
                            </div>
                        </div>
                    )}
                </div>

                {/* Content */}
                <div className="p-4">
                    <div className="flex items-start justify-between mb-2">
                        <h3 className="font-semibold text-avtovo-text text-lg leading-tight">
                            {car.make} {car.model}
                        </h3>
                        <span className="text-avtovo-accent font-bold text-lg ml-2 whitespace-nowrap">
              {car.price.toLocaleString('pl-PL')} zł
            </span>
                    </div>

                    <div className="grid grid-cols-2 gap-1.5 mt-3">
                        <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                            <Calendar size={12} />
                            <span>{car.year}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                            <Gauge size={12} />
                            <span>{car.mileage.toLocaleString('pl-PL')} km</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                            <Fuel size={12} />
                            <span>{FUEL_LABELS[car.fuelType] || car.fuelType}</span>
                        </div>
                        <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                            <MapPin size={12} />
                            <span>{car.city}</span>
                        </div>
                    </div>
                </div>
            </div>
        </Link>
    );
}