import type { ApibaraVehicle } from '../types/auctions';
import { Gauge, MapPin, Calendar, Gavel, Camera } from 'lucide-react';

const USD = (v: number | undefined) => v == null ? '—' : `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;

interface Props {
    vehicle: ApibaraVehicle;
    onSelect: (vehicle: ApibaraVehicle) => void;
}

export default function ApibaraVehicleBriefCard({ vehicle, onSelect }: Props) {
    return (
        <button
            onClick={() => onSelect(vehicle)}
            className="text-left bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden transition-all duration-200 hover:border-gray-600 hover:shadow-lg hover:shadow-black/20"
        >
            <div className="aspect-[16/10] bg-avtovo-bg flex items-center justify-center relative text-avtovo-border">
                <Gavel size={40} />
                <span className="absolute top-2 right-2 text-xs font-medium px-2 py-1 rounded-md bg-black/60 text-white uppercase">
                    {vehicle.platform}
                </span>
                {vehicle.media?.thumbsCount ? (
                    <span className="absolute bottom-2 right-2 flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-md bg-black/60 text-white">
                        <Camera size={12} /> {vehicle.media.thumbsCount}
                    </span>
                ) : null}
            </div>
            <div className="p-4">
                <div className="flex items-start justify-between mb-2 gap-2">
                    <h3 className="font-semibold text-avtovo-text text-base leading-tight">
                        {vehicle.year} {vehicle.make} {vehicle.model}
                    </h3>
                    <span className="text-avtovo-accent font-bold text-base whitespace-nowrap">
                        {USD(vehicle.pricing?.currentBidUsd)}
                    </span>
                </div>
                <div className="grid grid-cols-2 gap-1.5">
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                        <Calendar size={12} />
                        <span>Lot #{vehicle.lotNumber}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                        <Gauge size={12} />
                        <span>{vehicle.odometer?.mi != null ? `${vehicle.odometer.mi.toLocaleString('en-US')} mi` : '—'}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs col-span-2">
                        <MapPin size={12} />
                        <span>{vehicle.location?.display ?? '—'}{vehicle.condition?.primaryDamage ? ` · ${vehicle.condition.primaryDamage}` : ''}</span>
                    </div>
                </div>
            </div>
        </button>
    );
}
