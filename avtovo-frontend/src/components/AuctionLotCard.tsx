import type { AuctionLot } from '../types/auctions';
import { Gauge, MapPin, Calendar, Gavel } from 'lucide-react';

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
    LIVE: { label: 'Na żywo', className: 'bg-avtovo-accent/15 text-avtovo-accent' },
    SOLD: { label: 'Sprzedane', className: 'bg-emerald-500/15 text-emerald-400' },
    UNSOLD: { label: 'Niesprzedane', className: 'bg-avtovo-border text-avtovo-text-secondary' },
    EXPIRED: { label: 'Wygasłe', className: 'bg-avtovo-border text-avtovo-text-secondary' },
    REMOVED: { label: 'Usunięte', className: 'bg-avtovo-border text-avtovo-text-secondary' },
};

interface Props {
    lot: AuctionLot;
    onSelect: (lot: AuctionLot) => void;
    selected?: boolean;
}

export default function AuctionLotCard({ lot, onSelect, selected }: Props) {
    const image = lot.imageUrls?.[0];
    const status = STATUS_LABELS[lot.status] ?? STATUS_LABELS.LIVE;

    return (
        <button
            onClick={() => onSelect(lot)}
            className={`text-left bg-avtovo-card border rounded-xl overflow-hidden transition-all duration-200 hover:border-gray-600 hover:shadow-lg hover:shadow-black/20 ${
                selected ? 'border-avtovo-accent' : 'border-avtovo-border'
            }`}
        >
            <div className="aspect-[16/10] bg-avtovo-bg overflow-hidden relative">
                {image ? (
                    <img src={image} alt={`${lot.make} ${lot.model}`} className="w-full h-full object-cover" />
                ) : (
                    <div className="w-full h-full flex items-center justify-center text-avtovo-border">
                        <Gavel size={40} />
                    </div>
                )}
                <span className={`absolute top-2 left-2 text-xs font-medium px-2 py-1 rounded-md ${status.className}`}>
                    {status.label}
                </span>
                <span className="absolute top-2 right-2 text-xs font-medium px-2 py-1 rounded-md bg-black/60 text-white">
                    {lot.source}
                </span>
            </div>
            <div className="p-4">
                <div className="flex items-start justify-between mb-2 gap-2">
                    <h3 className="font-semibold text-avtovo-text text-base leading-tight">
                        {lot.year} {lot.make} {lot.model}
                    </h3>
                    <span className="text-avtovo-accent font-bold text-base whitespace-nowrap">
                        {lot.auctionPrice != null ? `$${lot.auctionPrice.toLocaleString('en-US')}` : '—'}
                    </span>
                </div>
                <div className="grid grid-cols-2 gap-1.5">
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                        <Calendar size={12} />
                        <span>Lot #{lot.lot_number}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs">
                        <Gauge size={12} />
                        <span>{lot.odometer != null ? `${lot.odometer.toLocaleString('en-US')} ${lot.odometerUnit ?? 'mi'}` : '—'}</span>
                    </div>
                    <div className="flex items-center gap-1.5 text-avtovo-text-secondary text-xs col-span-2">
                        <MapPin size={12} />
                        <span>{lot.auctionLocation ?? '—'}{lot.primaryDamage ? ` · ${lot.primaryDamage}` : ''}</span>
                    </div>
                </div>
            </div>
        </button>
    );
}
