import { useCallback, useState } from 'react';
import { Loader2, RotateCcw } from 'lucide-react';
import { getNotificationStats, listNotifications, retryNotification, type AdminNotification } from '../api';
import { Badge } from '../components/Badges';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import { useToast } from '../components/Toasts';
import { errorMessage, useLoader, useQueryState } from '../hooks';
import { NOTIFICATION_STATUS_LABELS, NOTIFICATION_TYPE_LABELS, formatDateTime } from '../labels';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

/** /admin/powiadomienia — the email outbox, failed ones first, with retry. */
export default function NotificationsPage() {
    const toast = useToast();
    const { values, update, query } = useQueryState({ status: 'FAILED', type: '', page: '0' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const loader = useCallback(() => listNotifications(values.status, values.type, Number(values.page) || 0), [query]);
    const { data, error, loading, reload } = useLoader(loader);
    const statsLoader = useCallback(() => getNotificationStats(), []);
    const stats = useLoader(statsLoader);
    const [retrying, setRetrying] = useState<string | null>(null);

    const retry = async (n: AdminNotification) => {
        setRetrying(n.id);
        try {
            await retryNotification(n.id);
            toast.success('Powiadomienie wróciło do kolejki wysyłki.');
            reload();
            stats.reload();
        } catch (e) {
            toast.error(errorMessage(e, 'Nie udało się ponowić wysyłki.'));
        } finally {
            setRetrying(null);
        }
    };

    const columns: Column<AdminNotification>[] = [
        { key: 'created', header: 'Utworzono', render: n => <span className="whitespace-nowrap">{formatDateTime(n.createdAt)}</span> },
        { key: 'type', header: 'Rodzaj', render: n => NOTIFICATION_TYPE_LABELS[n.type] ?? n.type },
        { key: 'recipient', header: 'Odbiorca', render: n => <span className="break-all text-avtovo-text-secondary">{n.recipientEmail ?? n.recipientId}</span> },
        {
            key: 'status', header: 'Status', render: n => (
                <Badge tone={n.status === 'FAILED' ? 'red' : n.status === 'SENT' ? 'green' : n.status === 'PENDING' ? 'yellow' : 'gray'}>
                    {NOTIFICATION_STATUS_LABELS[n.status] ?? n.status}
                </Badge>
            ),
        },
        { key: 'attempts', header: 'Próby', render: n => n.attempts },
        { key: 'error', header: 'Ostatni błąd', render: n => <span className="text-xs text-red-300 break-all">{n.lastError ?? ''}</span> },
        {
            key: 'actions', header: '', render: n => n.status === 'FAILED' && (
                <button onClick={() => retry(n)} disabled={retrying === n.id}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs border border-avtovo-border hover:bg-white/5 disabled:opacity-50">
                    {retrying === n.id ? <Loader2 size={13} className="animate-spin" /> : <RotateCcw size={13} />} Ponów
                </button>
            ),
        },
    ];

    return (
        <>
            <PageHeader
                title="Powiadomienia"
                subtitle={stats.data ? `Nieudane: ${stats.data.byStatus.FAILED ?? 0} (24 h: ${stats.data.failedLast24h}) · wysłane w 7 dni: ${stats.data.sentLast7Days} · w kolejce: ${stats.data.pending}` : undefined}
            />
            <div className="flex flex-wrap gap-2 mb-4">
                <select value={values.status} onChange={e => update({ status: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie statusy</option>
                    {Object.entries(NOTIFICATION_STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <select value={values.type} onChange={e => update({ type: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie rodzaje</option>
                    {Object.entries(NOTIFICATION_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
            </div>
            <DataTable
                columns={columns}
                rows={data?.content}
                rowKey={n => n.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText={values.status === 'FAILED' ? 'Brak nieudanych powiadomień' : 'Brak powiadomień'}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
        </>
    );
}
