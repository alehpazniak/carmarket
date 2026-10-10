import {useEffect, useState} from 'react';
import {Loader2, Sparkles, X} from 'lucide-react';
import {formatAmount, getPaymentProducts, LISTING_PRODUCT_CODES, startPayment} from '../api/payments';
import type {CarListing, PaymentProduct} from '../types';
import {activeUntilLabel} from '../utils/promotion';

interface Props {
    car: CarListing;
    onClose: () => void;
}

/** Owner buys a promotion/highlight for an ACTIVE listing; payment continues on Przelewy24. */
export default function PromoteListingDialog({car, onClose}: Props) {
    const [products, setProducts] = useState<PaymentProduct[] | null>(null);
    const [selected, setSelected] = useState<string | null>(null);
    const [paying, setPaying] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getPaymentProducts()
            .then(all => {
                const listingProducts = all.filter(p => LISTING_PRODUCT_CODES.includes(p.code));
                setProducts(listingProducts);
                setSelected(listingProducts[0]?.code ?? null);
            })
            .catch(() => setError('Nie udało się pobrać cennika.'));
    }, []);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && !paying) onClose();
        };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [onClose, paying]);

    const handlePay = async () => {
        if (!selected) return;
        setPaying(true);
        setError(null);
        try {
            await startPayment(selected, car.id); // leaves the app on success
        } catch (err) {
            const status = (err as { response?: { status?: number } }).response?.status;
            setError(
                status === 403 ? 'Możesz promować tylko własne ogłoszenia.'
                    : status === 409 ? 'Promować można tylko aktywne ogłoszenia.'
                        : status === 503 ? 'Płatności są chwilowo niedostępne.'
                            : 'Nie udało się rozpocząć płatności. Spróbuj ponownie.');
            setPaying(false);
        }
    };

    const promotedUntil = activeUntilLabel(car.promotedUntil);
    const highlightedUntil = activeUntilLabel(car.highlightedUntil);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
             onClick={() => !paying && onClose()}>
            <div role="dialog" aria-modal="true" aria-labelledby="promote-title"
                 className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 w-full max-w-md"
                 onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-1">
                    <h2 id="promote-title" className="text-lg font-semibold text-avtovo-text flex items-center gap-2">
                        <Sparkles size={18} className="text-avtovo-accent"/> Promuj ogłoszenie
                    </h2>
                    <button type="button" onClick={onClose} disabled={paying} title="Zamknij"
                            className="text-avtovo-text-secondary hover:text-avtovo-text disabled:opacity-50">
                        <X size={18}/>
                    </button>
                </div>
                <p className="text-avtovo-text-secondary text-sm mb-4">{car.make} {car.model} · {car.year}</p>

                {(promotedUntil || highlightedUntil) && (
                    <div className="text-sm text-avtovo-text bg-avtovo-bg border border-avtovo-border rounded-lg px-4 py-3 mb-4 space-y-1">
                        {promotedUntil && <p>Promowane do {promotedUntil}</p>}
                        {highlightedUntil && <p>Wyróżnione do {highlightedUntil}</p>}
                        <p className="text-avtovo-text-secondary text-xs">Kolejny zakup przedłuża aktualny okres.</p>
                    </div>
                )}

                {products === null && !error ? (
                    <div className="flex justify-center py-6">
                        <Loader2 size={24} className="animate-spin text-avtovo-accent"/>
                    </div>
                ) : (
                    <div className="space-y-2 mb-4">
                        {products?.map(p => (
                            <label key={p.code}
                                   className={`flex items-start gap-3 cursor-pointer rounded-lg border px-4 py-3 transition-colors ${
                                       selected === p.code ? 'border-avtovo-accent bg-avtovo-accent/10' : 'border-avtovo-border hover:border-gray-600'}`}>
                                <input type="radio" name="promotion-product" value={p.code}
                                       checked={selected === p.code} onChange={() => setSelected(p.code)}
                                       className="mt-1 accent-avtovo-accent"/>
                                <span className="flex-1 text-sm text-avtovo-text">{p.description}</span>
                                <span className="text-sm font-semibold text-avtovo-accent whitespace-nowrap">
                                    {formatAmount(p.amount, p.currency)}
                                </span>
                            </label>
                        ))}
                    </div>
                )}

                {error && <p className="text-red-400 text-sm mb-4">{error}</p>}

                <button type="button" onClick={handlePay} disabled={!selected || paying}
                        className="w-full flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white py-3 rounded-xl font-medium transition-colors disabled:opacity-60">
                    {paying && <Loader2 size={16} className="animate-spin"/>}
                    {paying ? 'Przekierowanie do Przelewy24…' : 'Zapłać'}
                </button>
            </div>
        </div>
    );
}
