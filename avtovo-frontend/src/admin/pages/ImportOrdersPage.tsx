import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { X } from 'lucide-react';
import { listImportOrders } from '../../api/importOrders';
import StatusBadge from '../../components/importOrders/StatusBadge';
import { ALL_STATUSES, SOURCE_LABELS, STATUS_LABELS, carTitle } from '../../constants/importOrders';
import type { ImportOrderStatus, ImportOrderSummary } from '../../types/importOrders';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import SearchInput from '../components/SearchInput';
import { useLoader, useQueryState } from '../hooks';
import { formatDateTime } from '../labels';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

/** /admin/zlecenia — every dealer's import orders, read-only (changes stay with the owning dealer). */
export default function ImportOrdersPage() {
    const navigate = useNavigate();
    const { values, update, query } = useQueryState({ q: '', status: '', dealerId: '', archived: '', page: '0' });
    const loader = useCallback(() => listImportOrders({
        q: values.q, status: values.status as ImportOrderStatus | '', dealerId: values.dealerId || undefined,
        archived: values.archived === 'true', page: Number(values.page) || 0, size: 20,
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }), [query]);
    const { data, error, loading, reload } = useLoader(loader);

    const columns: Column<ImportOrderSummary>[] = [
        {
            key: 'car', header: 'Pojazd', render: o => (
                <div className="min-w-[12rem]">
                    <div className="font-medium">{carTitle(o)}</div>
                    <div className="text-xs text-avtovo-text-secondary">{SOURCE_LABELS[o.source]}{o.lotNumber ? ` · lot ${o.lotNumber}` : ''}</div>
                </div>
            ),
        },
        { key: 'status', header: 'Status', render: o => <StatusBadge status={o.status} /> },
        { key: 'client', header: 'Klient', render: o => o.clientName ?? '—' },
        {
            key: 'dealer', header: 'Dealer', render: o => (
                <button onClick={e => { e.stopPropagation(); update({ dealerId: o.dealerId }); }}
                        className="text-avtovo-accent hover:underline text-xs" title="Pokaż zlecenia tego dealera">
                    {o.dealerId.slice(0, 8)}…
                </button>
            ),
        },
        { key: 'created', header: 'Utworzono', render: o => <span className="whitespace-nowrap">{formatDateTime(o.createdAt)}</span> },
    ];

    return (
        <>
            <PageHeader title="Zlecenia importu" subtitle="Podgląd zleceń wszystkich dealerów (tylko do odczytu)" />
            <div className="flex flex-wrap gap-2 mb-4">
                <SearchInput key={`q-${values.q}`} value={values.q} onChange={v => update({ q: v })} placeholder="VIN, lot, klient, marka, model" />
                <select value={values.status} onChange={e => update({ status: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie statusy</option>
                    {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
                </select>
                <select value={values.archived} onChange={e => update({ archived: e.target.value })} className={inputClass}>
                    <option value="">Aktywne</option>
                    <option value="true">Zarchiwizowane</option>
                </select>
                {values.dealerId && (
                    <button onClick={() => update({ dealerId: '' })}
                            className="inline-flex items-center gap-1 px-3 py-2 rounded-lg text-sm border border-avtovo-accent text-avtovo-accent">
                        Dealer {values.dealerId.slice(0, 8)}… <X size={14} />
                    </button>
                )}
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={o => o.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak zleceń dla tych filtrów"
                onRowClick={o => navigate(`/panel-dealera/zlecenia/${o.id}`)}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
        </>
    );
}
