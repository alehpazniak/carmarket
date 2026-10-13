import { useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Flag, X } from 'lucide-react';
import { listAdminListings, type AdminListingRow } from '../api';
import DataTable, { type Column } from '../components/DataTable';
import ListingStatusBadge from '../components/ListingStatusBadge';
import PageHeader from '../components/PageHeader';
import SearchInput from '../components/SearchInput';
import { useLoader, useQueryState } from '../hooks';
import { formatDateTime } from '../labels';
import { LISTING_STATUS_LABELS } from '../../constants/reports';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

const columns: Column<AdminListingRow>[] = [
    {
        key: 'listing', header: 'Ogłoszenie', render: l => (
            <div className="flex items-center gap-3 min-w-[14rem]">
                {l.primaryImageUrl
                    ? <img src={l.primaryImageUrl} alt="" className="w-14 h-10 object-cover rounded-md bg-avtovo-bg" />
                    : <div className="w-14 h-10 rounded-md bg-avtovo-bg" />}
                <div>
                    <div className="font-medium">{l.make} {l.model} {l.year}</div>
                    <div className="text-xs text-avtovo-text-secondary">{l.city ?? '—'}</div>
                </div>
            </div>
        ),
    },
    { key: 'price', header: 'Cena', sortField: 'price', render: l => <span className="whitespace-nowrap">{Number(l.price).toLocaleString('pl-PL')} zł</span> },
    { key: 'status', header: 'Status', render: l => <ListingStatusBadge status={l.status} /> },
    {
        key: 'seller', header: 'Sprzedawca', render: l => (
            <Link to={`/admin/uzytkownicy/${l.sellerId}`} onClick={e => e.stopPropagation()} className="text-avtovo-accent hover:underline text-xs break-all">
                {l.sellerId.slice(0, 8)}…
            </Link>
        ),
    },
    {
        key: 'reports', header: 'Zgłoszenia', sortField: 'openReports', render: l => l.openReports > 0
            ? <span className="inline-flex items-center gap-1 text-red-400"><Flag size={13} /> {l.openReports}</span>
            : <span className="text-avtovo-muted">0</span>,
    },
    { key: 'created', header: 'Dodano', sortField: 'createdAt', render: l => <span className="whitespace-nowrap">{formatDateTime(l.createdAt)}</span> },
];

/** /admin/ogloszenia — every listing, REMOVED included. */
export default function ListingsPage() {
    const navigate = useNavigate();
    const { values, update, query } = useQueryState({
        status: '', sellerId: '', q: '', promoted: '', from: '', to: '', sort: 'createdAt,desc', page: '0',
    });
    const loader = useCallback(() => listAdminListings({
        status: values.status, sellerId: values.sellerId, q: values.q, promoted: values.promoted, from: values.from,
        to: values.to, sort: values.sort, page: Number(values.page) || 0, size: 20,
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }), [query]);
    const { data, error, loading, reload } = useLoader(loader);

    return (
        <>
            <PageHeader title="Ogłoszenia" />
            <div className="flex flex-wrap gap-2 mb-4">
                <SearchInput key={`q-${values.q}`} value={values.q} onChange={v => update({ q: v })} placeholder="Marka, model, miasto lub ID" />
                <select value={values.status} onChange={e => update({ status: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie statusy</option>
                    {Object.entries(LISTING_STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <select value={values.promoted} onChange={e => update({ promoted: e.target.value })} className={inputClass}>
                    <option value="">Promowanie: wszystkie</option>
                    <option value="true">Promowane lub wyróżnione</option>
                    <option value="false">Bez promowania</option>
                </select>
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    od <input type="date" value={values.from} onChange={e => update({ from: e.target.value })} className={inputClass} />
                </label>
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    do <input type="date" value={values.to} onChange={e => update({ to: e.target.value })} className={inputClass} />
                </label>
                {values.sellerId && (
                    <button onClick={() => update({ sellerId: '' })}
                            className="inline-flex items-center gap-1 px-3 py-2 rounded-lg text-sm border border-avtovo-accent text-avtovo-accent">
                        Sprzedawca {values.sellerId.slice(0, 8)}… <X size={14} />
                    </button>
                )}
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={l => l.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak ogłoszeń dla tych filtrów"
                onRowClick={l => navigate(`/admin/ogloszenia/${l.id}`)}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
                sort={values.sort}
                onSortChange={sort => update({ sort })}
            />
        </>
    );
}
