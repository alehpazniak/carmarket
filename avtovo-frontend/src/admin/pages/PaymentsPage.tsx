import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Download, Loader2, X } from 'lucide-react';
import { downloadPaymentsCsv, getPaymentStats, listAdminPayments, type AdminPayment } from '../api';
import { Badge } from '../components/Badges';
import BarChart from '../components/BarChart';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import { useToast } from '../components/Toasts';
import { errorMessage, useLoader, useQueryState } from '../hooks';
import { PAYMENT_STATUS_LABELS, PRODUCT_LABELS, formatDateTime, formatMoney } from '../labels';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

export function PaymentStatusBadge({ status }: { status: string }) {
    const tone = status === 'PAID' ? 'green' : status === 'FAILED' ? 'red' : status === 'REFUNDED' ? 'yellow' : 'gray';
    return <Badge tone={tone}>{PAYMENT_STATUS_LABELS[status] ?? status}</Badge>;
}

const columns: Column<AdminPayment>[] = [
    { key: 'created', header: 'Utworzono', render: p => <span className="whitespace-nowrap">{formatDateTime(p.createdAt)}</span> },
    { key: 'product', header: 'Produkt', render: p => PRODUCT_LABELS[p.productCode] ?? p.productCode },
    { key: 'amount', header: 'Kwota', render: p => <span className="whitespace-nowrap">{formatMoney(p.amount, p.currency)}</span> },
    { key: 'status', header: 'Status', render: p => <PaymentStatusBadge status={p.status} /> },
    { key: 'email', header: 'Płacący', render: p => <span className="text-avtovo-text-secondary break-all">{p.email}</span> },
];

/** /admin/platnosci — payments across all users, CSV export with the same filters. */
export default function PaymentsPage() {
    const navigate = useNavigate();
    const toast = useToast();
    const { values, update, query } = useQueryState({ status: '', productCode: '', userId: '', from: '', to: '', page: '0' });
    const filters = { status: values.status, productCode: values.productCode, userId: values.userId, from: values.from, to: values.to };
    const loader = useCallback(() => listAdminPayments({ ...filters, page: Number(values.page) || 0, size: 20 }),
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [query]);
    const { data, error, loading, reload } = useLoader(loader);
    const statsLoader = useCallback(() => getPaymentStats(values.from || undefined, values.to || undefined), [values.from, values.to]);
    const stats = useLoader(statsLoader);
    const [exporting, setExporting] = useState(false);

    const exportCsv = async () => {
        setExporting(true);
        try {
            const truncated = await downloadPaymentsCsv(filters);
            if (truncated) toast.error('Plik zawiera tylko 10 000 najnowszych płatności — zawęź filtry.');
        } catch (e) {
            toast.error(errorMessage(e, 'Nie udało się pobrać pliku CSV.'));
        } finally {
            setExporting(false);
        }
    };

    return (
        <>
            <PageHeader
                title="Płatności"
                subtitle={stats.data ? `Przychód ${formatMoney(stats.data.revenue)} · zwroty ${formatMoney(stats.data.refundedAmount)} (${stats.data.from} – ${stats.data.to})` : undefined}
                actions={
                    <button onClick={exportCsv} disabled={exporting}
                            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text hover:bg-white/5 disabled:opacity-50">
                        {exporting ? <Loader2 size={15} className="animate-spin" /> : <Download size={15} />} Eksport CSV
                    </button>
                }
            />
            {stats.data && (
                <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 mb-4">
                    <BarChart label="Przychód dzienny" format={v => formatMoney(v)} points={stats.data.daily.map(d => ({ date: d.date, value: d.amount }))} />
                </div>
            )}
            <div className="flex flex-wrap gap-2 mb-4">
                <select value={values.status} onChange={e => update({ status: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie statusy</option>
                    {Object.entries(PAYMENT_STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <select value={values.productCode} onChange={e => update({ productCode: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie produkty</option>
                    {Object.entries(PRODUCT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    od <input type="date" value={values.from} onChange={e => update({ from: e.target.value })} className={inputClass} />
                </label>
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    do <input type="date" value={values.to} onChange={e => update({ to: e.target.value })} className={inputClass} />
                </label>
                {values.userId && (
                    <button onClick={() => update({ userId: '' })}
                            className="inline-flex items-center gap-1 px-3 py-2 rounded-lg text-sm border border-avtovo-accent text-avtovo-accent">
                        Użytkownik {values.userId.slice(0, 8)}… <X size={14} />
                    </button>
                )}
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={p => p.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak płatności dla tych filtrów"
                onRowClick={p => navigate(`/admin/platnosci/${p.id}`)}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
        </>
    );
}
