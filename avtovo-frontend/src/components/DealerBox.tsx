import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, ChevronRight, MapPin } from 'lucide-react';
import { getDealerCard } from '../api/dealers';
import type { DealerCard } from '../types/dealers';

/** On a listing page: the seller's dealer page (logo, name, city) when they have a published one. */
export default function DealerBox({ sellerId }: { sellerId: string }) {
    const [dealer, setDealer] = useState<DealerCard | null>(null);

    useEffect(() => {
        let cancelled = false;
        getDealerCard(sellerId)
            .then(d => !cancelled && setDealer(d))
            .catch(() => !cancelled && setDealer(null));
        return () => {
            cancelled = true;
        };
    }, [sellerId]);

    if (!dealer) return null;
    return (
        <Link to={`/dealer/${dealer.slug}`}
              className="flex items-center gap-3 bg-avtovo-card border border-avtovo-border rounded-xl p-3 hover:border-avtovo-accent/60 transition-colors">
            <div className="w-12 h-12 rounded-lg bg-white overflow-hidden flex items-center justify-center shrink-0">
                {dealer.logoUrl
                    ? <img src={dealer.logoUrl} alt={`${dealer.companyName} — logo`} className="w-full h-full object-contain" />
                    : <Building2 size={22} className="text-avtovo-muted" />}
            </div>
            <div className="min-w-0 flex-1">
                <p className="text-xs text-avtovo-text-secondary">Dealer</p>
                <p className="font-medium text-avtovo-text truncate">{dealer.companyName}</p>
                {dealer.city && <p className="text-xs text-avtovo-text-secondary inline-flex items-center gap-1"><MapPin size={12} /> {dealer.city}</p>}
            </div>
            <span className="text-xs text-avtovo-accent inline-flex items-center whitespace-nowrap">
                Wszystkie ogłoszenia <ChevronRight size={14} />
            </span>
        </Link>
    );
}
