import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Eye, ShieldOff, XCircle } from 'lucide-react';
import { blockListing, dismissReports, listReportGroups, type ReportGroup } from '../api';
import ConfirmDialog from '../components/ConfirmDialog';
import DataTable, { type Column } from '../components/DataTable';
import ListingStatusBadge from '../components/ListingStatusBadge';
import PageHeader from '../components/PageHeader';
import { useToast } from '../components/Toasts';
import { useLoader, useQueryState } from '../hooks';
import { formatDateTime } from '../labels';
import { REPORT_REASON_LABELS, type ReportReason } from '../../constants/reports';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';
const STATUS_LABELS: Record<string, string> = { OPEN: 'Otwarte', RESOLVED: 'Rozpatrzone', DISMISSED: 'Odrzucone' };

/** /admin/zgloszenia — listings with reports, most-reported first. */
export default function ReportsPage() {
    const toast = useToast();
    const { values, update, query } = useQueryState({ status: 'OPEN', page: '0' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const loader = useCallback(() => listReportGroups(values.status, Number(values.page) || 0), [query]);
    const { data, error, loading, reload } = useLoader(loader);
    const [dialog, setDialog] = useState<{ kind: 'block' | 'dismiss'; group: ReportGroup } | null>(null);
    const open = values.status === 'OPEN';

    const columns: Column<ReportGroup>[] = [
        {
            key: 'listing', header: 'Ogłoszenie', render: g => (
                <div className="flex items-center gap-3 min-w-[14rem]">
                    {g.primaryImageUrl
                        ? <img src={g.primaryImageUrl} alt="" className="w-16 h-12 object-cover rounded-md bg-avtovo-bg" />
                        : <div className="w-16 h-12 rounded-md bg-avtovo-bg" />}
                    <div>
                        <div className="font-medium">{g.title ?? 'Ogłoszenie usunięte'}</div>
                        <ListingStatusBadge status={g.listingStatus} />
                    </div>
                </div>
            ),
        },
        { key: 'count', header: 'Zgłoszeń', render: g => <span className="text-lg font-semibold">{g.count}</span> },
        {
            key: 'reasons', header: 'Powody', render: g => (
                <ul className="text-xs text-avtovo-text-secondary space-y-0.5">
                    {g.reasons.map(r => <li key={r}>{REPORT_REASON_LABELS[r as ReportReason] ?? r}</li>)}
                </ul>
            ),
        },
        { key: 'latest', header: 'Ostatnie', render: g => <span className="whitespace-nowrap">{formatDateTime(g.latestAt)}</span> },
        {
            key: 'actions', header: '', render: g => (
                <div className="flex flex-wrap gap-2 justify-end">
                    <Link to={`/admin/ogloszenia/${g.carId}`} className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs border border-avtovo-border hover:bg-white/5">
                        <Eye size={13} /> Podgląd
                    </Link>
                    {open && g.listingStatus && g.listingStatus !== 'BLOCKED' && g.listingStatus !== 'REMOVED' && (
                        <button onClick={() => setDialog({ kind: 'block', group: g })}
                                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs border border-red-800 text-red-300 hover:bg-red-500/10">
                            <ShieldOff size={13} /> Zablokuj ogłoszenie
                        </button>
                    )}
                    {open && (
                        <button onClick={() => setDialog({ kind: 'dismiss', group: g })}
                                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs border border-avtovo-border hover:bg-white/5">
                            <XCircle size={13} /> Odrzuć zgłoszenia
                        </button>
                    )}
                </div>
            ),
        },
    ];

    return (
        <>
            <PageHeader title="Zgłoszenia" subtitle="Ogłoszenia zgłoszone przez użytkowników, od najczęściej zgłaszanych" />
            <div className="flex gap-2 mb-4">
                <select value={values.status} onChange={e => update({ status: e.target.value })} className={inputClass}>
                    {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={g => g.carId}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText={open ? 'Brak otwartych zgłoszeń' : 'Brak zgłoszeń'}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
            {dialog?.kind === 'block' && (
                <ConfirmDialog
                    title={`Zablokuj: ${dialog.group.title ?? 'ogłoszenie'}`}
                    message={`Zgłoszenia (${dialog.group.count}) zostaną zamknięte, a sprzedawca dostanie e-mail z powodem.`}
                    reasonLabel="Powód (sprzedawca zobaczy go w e-mailu)"
                    confirmLabel="Zablokuj"
                    destructive
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await blockListing(dialog.group.carId, reason);
                        toast.success('Ogłoszenie zostało zablokowane.');
                        reload();
                    }}
                />
            )}
            {dialog?.kind === 'dismiss' && (
                <ConfirmDialog
                    title="Odrzuć zgłoszenia"
                    message="Ogłoszenie pozostanie widoczne; zgłoszenia zostaną zamknięte jako bezzasadne."
                    reasonLabel="Notatka (widoczna w dzienniku zdarzeń)"
                    confirmLabel="Odrzuć zgłoszenia"
                    onClose={() => setDialog(null)}
                    onConfirm={async note => {
                        await dismissReports(dialog.group.carId, note);
                        toast.success('Zgłoszenia zostały odrzucone.');
                        reload();
                    }}
                />
            )}
        </>
    );
}
