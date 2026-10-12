import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, RotateCcw } from 'lucide-react';
import { getAdminPayment, refundPayment } from '../api';
import ConfirmDialog from '../components/ConfirmDialog';
import PageHeader from '../components/PageHeader';
import { ErrorState, LoadingState } from '../components/States';
import { useToast } from '../components/Toasts';
import { useLoader } from '../hooks';
import { PRODUCT_LABELS, formatDateTime, formatMoney } from '../labels';
import { PaymentStatusBadge } from './PaymentsPage';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div className="flex justify-between gap-4 py-2 border-b border-avtovo-border last:border-0 text-sm">
            <span className="text-avtovo-text-secondary">{label}</span>
            <span className="text-avtovo-text text-right break-all">{children}</span>
        </div>
    );
}

const LISTING_PRODUCTS = ['LISTING_PROMOTION', 'LISTING_HIGHLIGHT'];

/** /admin/platnosci/:id */
export default function PaymentDetailPage() {
    const { id = '' } = useParams();
    const toast = useToast();
    const loader = useCallback(() => getAdminPayment(id), [id]);
    const { data: p, error, loading, reload } = useLoader(loader);
    const [refundOpen, setRefundOpen] = useState(false);

    if (loading) return <LoadingState />;
    if (error || !p) return <ErrorState label="Nie udało się wczytać płatności." onRetry={reload} />;

    return (
        <>
            <Link to="/admin/platnosci" className="inline-flex items-center gap-1 text-sm text-avtovo-text-secondary hover:text-avtovo-text mb-3">
                <ArrowLeft size={14} /> Płatności
            </Link>
            <PageHeader
                title={`${PRODUCT_LABELS[p.productCode] ?? p.productCode} · ${formatMoney(p.amount, p.currency)}`}
                subtitle={p.description}
                actions={p.status === 'PAID' && (
                    <button onClick={() => setRefundOpen(true)}
                            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-red-800 text-red-300 hover:bg-red-500/10">
                        <RotateCcw size={15} /> Zwróć płatność
                    </button>
                )}
            />
            <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-5 max-w-2xl">
                <Row label="Status"><PaymentStatusBadge status={p.status} /></Row>
                <Row label="Płacący"><Link to={`/admin/uzytkownicy/${p.userId}`} className="text-avtovo-accent hover:underline">{p.email}</Link></Row>
                <Row label="Dotyczy">
                    {p.referenceId && LISTING_PRODUCTS.includes(p.productCode)
                        ? <Link to={`/admin/ogloszenia/${p.referenceId}`} className="text-avtovo-accent hover:underline">ogłoszenie {p.referenceId.slice(0, 8)}…</Link>
                        : p.referenceId ?? '—'}
                </Row>
                <Row label="Utworzono">{formatDateTime(p.createdAt)}</Row>
                <Row label="Opłacono">{formatDateTime(p.paidAt)}</Row>
                <Row label="Zwrócono">{formatDateTime(p.refundedAt)}</Row>
                <Row label="Przelewy24 (order id)">{p.p24OrderId ?? '—'}</Row>
                {p.failureReason && <Row label="Błąd">{p.failureReason}</Row>}
                <Row label="ID">{p.id}</Row>
            </section>
            {refundOpen && (
                <ConfirmDialog
                    title="Zwróć płatność"
                    message={`Przelewy24 zwróci ${formatMoney(p.amount, p.currency)} płacącemu; promowanie ogłoszenia zostanie cofnięte.`}
                    confirmLabel="Zwróć płatność"
                    destructive
                    onClose={() => setRefundOpen(false)}
                    onConfirm={async reason => {
                        await refundPayment(p.id, reason);
                        toast.success('Zlecono zwrot płatności.');
                        reload();
                    }}
                />
            )}
        </>
    );
}
