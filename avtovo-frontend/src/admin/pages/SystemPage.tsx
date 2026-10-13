import { useCallback } from 'react';
import { RotateCw } from 'lucide-react';
import { getSystemHealth, type InstanceHealth } from '../api';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import { useLoader } from '../hooks';
import { formatDateTime } from '../labels';

export function StatusPill({ status }: { status: string }) {
    const color = status === 'UP' ? 'bg-green-500/15 text-green-400 border-green-700'
        : status === 'DOWN' ? 'bg-red-500/15 text-red-400 border-red-700'
            : 'bg-yellow-500/15 text-yellow-300 border-yellow-700';
    return <span className={`inline-block px-2 py-0.5 rounded-md border text-xs font-medium ${color}`}>{status}</span>;
}

const columns: Column<InstanceHealth>[] = [
    { key: 'service', header: 'Serwis', render: h => <span className="font-medium">{h.service}</span> },
    { key: 'status', header: 'Status', render: h => <StatusPill status={h.status} /> },
    { key: 'instance', header: 'Instancja', render: h => <span className="text-avtovo-text-secondary break-all">{h.instanceId ?? '—'}</span> },
    { key: 'latency', header: 'Czas odpowiedzi', render: h => h.latencyMs != null ? `${h.latencyMs} ms` : '—' },
    { key: 'error', header: 'Błąd', render: h => <span className="text-avtovo-text-secondary">{h.error ?? ''}</span> },
];

/** /admin/system — /actuator/health of every instance registered in Eureka. */
export default function SystemPage() {
    const loader = useCallback(() => getSystemHealth(), []);
    const { data, error, loading, reload } = useLoader(loader);
    const down = data?.instances.filter(i => i.status !== 'UP').length ?? 0;

    return (
        <>
            <PageHeader
                title="System"
                subtitle={data ? `Sprawdzono ${formatDateTime(data.checkedAt)} · ${down === 0 ? 'wszystkie serwisy działają'
                    : `problemy: ${down}`}` : 'Stan serwisów zarejestrowanych w Eurece'}
                actions={
                    <button onClick={reload}
                            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text">
                        <RotateCw size={15} /> Odśwież
                    </button>
                }
            />
            <DataTable
                columns={columns}
                rows={data?.instances}
                rowKey={h => `${h.service}-${h.instanceId ?? 'missing'}`}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak zarejestrowanych serwisów"
            />
        </>
    );
}
