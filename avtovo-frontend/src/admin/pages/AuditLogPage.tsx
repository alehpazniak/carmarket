import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { getAuditLog, type AuditEntry } from '../api';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import SearchInput from '../components/SearchInput';
import { useLoader, useQueryState } from '../hooks';
import { ACTION_LABELS, SERVICES, TARGET_LABELS, actionLabel, formatDateTime } from '../labels';

const PAGE_SIZE = 50;
const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

/** yyyy-MM-dd (date input, local day) → ISO instant at local midnight; `endOfDay` gives the next midnight. */
function dayToIso(day: string, endOfDay = false): string | undefined {
    if (!day) return undefined;
    const d = new Date(`${day}T00:00:00`);
    if (endOfDay) d.setDate(d.getDate() + 1);
    return d.toISOString();
}

function targetLink(e: AuditEntry): string | null {
    if (!e.targetId) return null;
    if (e.targetType === 'USER') return `/admin/uzytkownicy/${e.targetId}`;
    if (e.targetType === 'LISTING') return `/admin/ogloszenia/${e.targetId}`;
    return null;
}

function Details({ details }: { details: Record<string, unknown> }) {
    const [open, setOpen] = useState(false);
    const entries = Object.entries(details ?? {});
    if (entries.length === 0) return null;
    return open ? (
        <div className="text-xs text-avtovo-text-secondary space-y-0.5">
            {entries.map(([k, v]) => <div key={k}><span className="text-avtovo-muted">{k}:</span> {typeof v === 'object' ? JSON.stringify(v) : String(v)}</div>)}
            <button onClick={() => setOpen(false)} className="underline">zwiń</button>
        </div>
    ) : (
        <button onClick={() => setOpen(true)} className="text-xs text-avtovo-accent hover:underline">szczegóły ({entries.length})</button>
    );
}

const columns: Column<AuditEntry>[] = [
    { key: 'when', header: 'Kiedy', render: e => <span className="whitespace-nowrap">{formatDateTime(e.occurredAt)}</span> },
    { key: 'actor', header: 'Kto', render: e => <span className="break-all">{e.actorEmail ?? (e.actorId === 'system' ? 'system' : e.actorId)}</span> },
    { key: 'action', header: 'Akcja', render: e => <><div>{actionLabel(e.action)}</div><div className="text-xs text-avtovo-muted">{e.service}</div></> },
    {
        key: 'target', header: 'Cel', render: e => {
            const link = targetLink(e);
            const label = `${TARGET_LABELS[e.targetType ?? ''] ?? e.targetType ?? ''}`;
            return (
                <div className="min-w-[8rem]">
                    <div className="text-xs text-avtovo-muted">{label}</div>
                    {link ? <Link to={link} className="text-avtovo-accent hover:underline break-all">{e.targetId}</Link>
                        : <span className="break-all">{e.targetId ?? '—'}</span>}
                </div>
            );
        },
    },
    { key: 'reason', header: 'Powód', render: e => <span className="text-avtovo-text-secondary">{e.reason ?? '—'}</span> },
    { key: 'details', header: '', render: e => <Details details={e.details} /> },
];

/** /admin/dziennik — admin-service audit log; every filter is in the URL (links from user/listing pages). */
export default function AuditLogPage() {
    const { values, update, query } = useQueryState({
        action: '', service: '', targetType: '', targetId: '', actorId: '', from: '', to: '', page: '0',
    });

    // `query` (the URL) is the dependency: values is a new object on every render
    const loader = useCallback(() => getAuditLog({
        action: values.action, service: values.service, targetType: values.targetType, targetId: values.targetId,
        actorId: values.actorId, from: dayToIso(values.from), to: dayToIso(values.to, true),
        page: Number(values.page) || 0, size: PAGE_SIZE,
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }), [query]);
    const { data, error, loading, reload } = useLoader(loader);
    const filtered = Object.entries(values).some(([k, v]) => k !== 'page' && v);

    return (
        <>
            <PageHeader title="Dziennik zdarzeń" subtitle="Każda zmiana wykonana przez administratora lub system" />
            <div className="flex flex-wrap gap-2 mb-4">
                <select value={values.action} onChange={e => update({ action: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie akcje</option>
                    {Object.entries(ACTION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <select value={values.service} onChange={e => update({ service: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie serwisy</option>
                    {SERVICES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                <select value={values.targetType} onChange={e => update({ targetType: e.target.value })} className={inputClass}>
                    <option value="">Każdy cel</option>
                    {Object.entries(TARGET_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <SearchInput key={`t-${values.targetId}`} value={values.targetId} onChange={v => update({ targetId: v })} placeholder="ID celu" />
                <SearchInput key={`a-${values.actorId}`} value={values.actorId} onChange={v => update({ actorId: v })} placeholder="ID administratora" />
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    od <input type="date" value={values.from} onChange={e => update({ from: e.target.value })} className={inputClass} />
                </label>
                <label className="flex items-center gap-1.5 text-sm text-avtovo-text-secondary">
                    do <input type="date" value={values.to} onChange={e => update({ to: e.target.value })} className={inputClass} />
                </label>
                {filtered && (
                    <button onClick={() => update({ action: '', service: '', targetType: '', targetId: '', actorId: '', from: '', to: '' })}
                            className="inline-flex items-center gap-1 px-3 py-2 text-sm text-avtovo-text-secondary hover:text-avtovo-text">
                        <X size={14} /> Wyczyść
                    </button>
                )}
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={e => e.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText={filtered ? 'Brak zdarzeń dla tych filtrów' : 'Dziennik jest pusty'}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
        </>
    );
}
