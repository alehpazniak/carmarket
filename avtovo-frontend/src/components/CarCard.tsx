import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type {CarDocument, CarListing} from '../types';
import { MapPin, Fuel, Gauge, Calendar, Pencil, MoreVertical, Tag, Trash2 } from 'lucide-react';

interface Props {
    car: CarListing | CarDocument;
    editHref?: string;
    onMarkSold?: (id: string) => void;
    onDelete?: (id: string) => void;
}

const FUEL_LABELS: Record<string, string> = {
    PETROL: 'Benzyna',
    DIESEL: 'Diesel',
    ELECTRIC: 'Elektryczny',
    HYBRID: 'Hybryda',
    LPG: 'LPG',
};

const STATUS_LABELS: Record<string, string> = {
    SOLD: 'Sprzedane',
    REMOVED: 'Usunięte',
};

export default function CarCard({ car, editHref, onMarkSold, onDelete }: Props) {
    const navigate = useNavigate();
    const mainImage = car.primaryImageUrl || car.imageUrls?.[0];
    const [menuOpen, setMenuOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const statusLabel = STATUS_LABELS[car.status];

    useEffect(() => {
        if (!menuOpen) return;
        const handleClickOutside = (e: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                setMenuOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [menuOpen]);

    return (
        <Link to={`/ogloszenia/${car.id}`} className="group block">
            <div className={`bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden hover:border-gray-600 transition-all duration-200 hover:shadow-lg hover:shadow-black/20 ${car.status !== 'ACTIVE' ? 'opacity-70' : ''}`}>
                {/* Image */}
                <div className="relative aspect-[16/10] bg-avtovo-bg overflow-hidden">
                    {statusLabel && (
                        <span className="absolute top-2 left-2 z-10 bg-black/70 text-white text-[11px] font-medium px-2 py-1 rounded-md">
                            {statusLabel}
                        </span>
                    )}
                    {editHref && (
                        <div ref={menuRef} className="absolute top-2 right-2 z-10">
                            <button
                                type="button"
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    setMenuOpen(prev => !prev);
                                }}
                                className="bg-black/70 hover:bg-black rounded-full p-2 transition-colors"
                                title="Opcje ogłoszenia"
                            >
                                <MoreVertical size={14} className="text-white"/>
                            </button>
                            {menuOpen && (
                                <div className="absolute right-0 mt-1 w-48 bg-avtovo-card border border-avtovo-border rounded-lg shadow-lg overflow-hidden">
                                    <button
                                        type="button"
                                        onClick={(e) => {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            setMenuOpen(false);
                                            navigate(editHref);
                                        }}
                                        className="w-full flex items-center gap-2 px-3 py-2 text-sm text-avtovo-text hover:bg-avtovo-bg text-left"
                                    >
                                        <Pencil size={14}/> Edytuj
                                    </button>
                                    {onMarkSold && car.status === 'ACTIVE' && (
                                        <button
                                            type="button"
                                            onClick={(e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                setMenuOpen(false);
                                                onMarkSold(car.id);
                                            }}
                                            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-avtovo-text hover:bg-avtovo-bg text-left"
                                        >
                                            <Tag size={14}/> Oznacz jako sprzedane
                                        </button>
                                    )}
                                    {onDelete && car.status !== 'REMOVED' && (
                                        <button
                                            type="button"
                                            onClick={(e) => {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                setMenuOpen(false);
                                                onDelete(car.id);
                                            }}
                                            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-red-500 hover:bg-avtovo-bg text-left"
                                        >
                                            <Trash2 size={14}/> Usuń
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
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