import {useEffect, useState} from 'react';
import {Link, useParams} from 'react-router-dom';
import {CheckCircle2, Loader2, XCircle, Clock} from 'lucide-react';
import {formatAmount, syncPayment} from '../api/payments';
import type {Payment} from '../types';

const POLL_INTERVAL_MS = 3000;
const MAX_ATTEMPTS = 20; // ~1 minute; P24 keeps notifying in the background anyway

/** Przelewy24 sends the customer here (urlReturn) after the payment page. */
export default function PaymentReturn() {
    const {id} = useParams<{ id: string }>();
    const [payment, setPayment] = useState<Payment | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [gaveUp, setGaveUp] = useState(false);

    useEffect(() => {
        if (!id) return;
        let cancelled = false;
        let attempts = 0;
        let timer: ReturnType<typeof setTimeout>;

        const poll = async () => {
            try {
                const p = await syncPayment(id);
                if (cancelled) return;
                setPayment(p);
                if (p.status !== 'PENDING') return;
            } catch {
                if (cancelled) return;
                setError('Nie udało się pobrać statusu płatności.');
                return;
            }
            attempts += 1;
            if (attempts >= MAX_ATTEMPTS) {
                setGaveUp(true);
                return;
            }
            timer = setTimeout(poll, POLL_INTERVAL_MS);
        };
        poll();

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [id]);

    let icon = <Loader2 size={40} className="animate-spin text-avtovo-accent mx-auto"/>;
    let title = 'Sprawdzamy płatność...';
    let text = 'To może potrwać kilka sekund.';

    if (error) {
        icon = <XCircle size={40} className="text-red-400 mx-auto"/>;
        title = 'Błąd';
        text = error;
    } else if (payment?.status === 'PAID') {
        icon = <CheckCircle2 size={40} className="text-green-400 mx-auto"/>;
        title = 'Płatność zakończona';
        text = `${payment.description} — ${formatAmount(payment.amount, payment.currency)}`;
    } else if (payment?.status === 'FAILED') {
        icon = <XCircle size={40} className="text-red-400 mx-auto"/>;
        title = 'Płatność nieudana';
        text = 'Transakcja nie została zrealizowana. Możesz spróbować ponownie.';
    } else if (payment?.status === 'REFUNDED') {
        icon = <CheckCircle2 size={40} className="text-avtovo-muted mx-auto"/>;
        title = 'Płatność zwrócona';
        text = `${formatAmount(payment.amount, payment.currency)} zostało zwrócone.`;
    } else if (gaveUp) {
        icon = <Clock size={40} className="text-avtovo-muted mx-auto"/>;
        title = 'Płatność w toku';
        text = 'Nie otrzymaliśmy jeszcze potwierdzenia od Przelewy24. Status zaktualizuje się automatycznie.';
    }

    return (
        <div className="min-h-screen bg-avtovo-bg flex items-center justify-center px-4">
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-8 max-w-md w-full text-center">
                {icon}
                <h1 className="text-xl font-bold text-avtovo-text mt-4 mb-2">{title}</h1>
                <p className="text-avtovo-text-secondary mb-6">{text}</p>
                <Link to="/moje-ogloszenia" className="text-avtovo-accent hover:text-avtovo-accent-hover">
                    Przejdź do moich ogłoszeń
                </Link>
            </div>
        </div>
    );
}
