import { useCallback, useState } from 'react';
import { Gauge, RefreshCw, RotateCw, Search } from 'lucide-react';
import { getAuctionStatus, reindexSearch, triggerAuctionSync, type AuctionStatus, type SyncRun } from '../api';
import { Badge } from '../components/Badges';
import ConfirmDialog from '../components/ConfirmDialog';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import { ErrorState, LoadingState } from '../components/States';
import { useToast } from '../components/Toasts';
import { useLoader } from '../hooks';
import { formatDateTime } from '../labels';

const card = 'bg-avtovo-card border border-avtovo-border rounded-xl p-5';
const actionButton = 'inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text hover:bg-white/5 disabled:opacity-50';

const runColumns: Column<SyncRun>[] = [
    { key: 'parser', header: 'Źródło', render: r => r.parser.replace(/Parser$/, '') },
    { key: 'started', header: 'Start', render: r => <span className="whitespace-nowrap">{formatDateTime(r.startedAt)}</span> },
    { key: 'trigger', header: 'Uruchomienie', render: r => r.trigger === 'MANUAL' ? 'ręczne' : 'automatyczne' },
    {
        key: 'result', header: 'Wynik', render: r => r.error ? <Badge tone="red">Błąd</Badge>
            : !r.finishedAt ? <Badge tone="yellow">W toku</Badge>
                : <span>{r.lotsFetched ?? 0} lotów · nowe {r.lotsAdded ?? 0} · zmienione {r.lotsUpdated ?? 0}</span>,
    },
    { key: 'requests', header: 'Zapytania API', render: r => r.apiRequests ?? '—' },
    { key: 'error', header: 'Błąd', render: r => <span className="text-xs text-red-300 break-all">{r.error ?? ''}</span> },
];

/** Plain values of the Apibara usage response (plan, limits, counters); nested objects are skipped. */
function usageEntries(usage: Record<string, unknown> | null): [string, string][] {
    if (!usage) return [];
    const flat = (usage.data && typeof usage.data === 'object' ? usage.data : usage) as Record<string, unknown>;
    return Object.entries(flat)
        .filter(([, v]) => v === null || ['string', 'number', 'boolean'].includes(typeof v))
        .map(([k, v]) => [k, String(v)]);
}

function QuotaRow({ label, value }: { label: string; value: string | number | null }) {
    return (
        <div className="flex justify-between gap-3">
            <dt className="text-avtovo-text-secondary">{label}</dt><dd className="text-avtovo-text">{value ?? '—'}</dd>
        </div>
    );
}

/** /admin/aukcje — Apibara, auction sync and the search index. */
export default function AuctionsPage() {
    const toast = useToast();
    const loader = useCallback(() => getAuctionStatus(), []);
    const { data, error, loading, reload } = useLoader(loader);
    const [dialog, setDialog] = useState<'sync' | 'reindex' | null>(null);
    // GET /usage is fetched only on request: Apibara doesn't say it's free of the monthly quota
    const [usage, setUsage] = useState<Pick<AuctionStatus, 'apibaraUsage' | 'apibaraUsageError'> | null>(null);
    const [usageLoading, setUsageLoading] = useState(false);

    const loadUsage = async () => {
        setUsageLoading(true);
        try {
            const status = await getAuctionStatus(true);
            setUsage({ apibaraUsage: status.apibaraUsage, apibaraUsageError: status.apibaraUsageError });
        } catch {
            setUsage({ apibaraUsage: null, apibaraUsageError: 'auction-import-service nie odpowiada' });
        } finally {
            setUsageLoading(false);
        }
    };

    if (loading && !data) return <LoadingState />;
    if (error || !data) return <ErrorState label="Dane niedostępne (auction-import-service)." onRetry={reload} />;

    return (
        <>
            <PageHeader
                title="Aukcje"
                actions={
                    <>
                        <button className={actionButton} onClick={reload}><RotateCw size={15} /> Odśwież</button>
                        <button className={actionButton} disabled={!data.apibaraConfigured || data.syncRunning} onClick={() => setDialog('sync')}>
                            <RefreshCw size={15} className={data.syncRunning ? 'animate-spin' : ''} />
                            {data.syncRunning ? 'Synchronizacja trwa…' : 'Synchronizuj teraz'}
                        </button>
                        <button className={actionButton} onClick={() => setDialog('reindex')}><Search size={15} /> Przeindeksuj wyszukiwarkę</button>
                    </>
                }
            />
            <div className="grid gap-4 lg:grid-cols-2 mb-4">
                <section className={card}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Apibara</h2>
                    {!data.apibaraConfigured ? (
                        <p className="text-sm text-yellow-300">Brak klucza APIBARA_API_KEY — synchronizacja jest wyłączona.</p>
                    ) : (
                        <div className="space-y-4">
                            {data.apibaraQuota ? (
                                <div className="space-y-1.5">
                                    <dl className="text-sm space-y-1.5">
                                        <QuotaRow label="Pozostało zapytań" value={data.apibaraQuota.remaining != null && data.apibaraQuota.limit != null
                                            ? `${data.apibaraQuota.remaining} z ${data.apibaraQuota.limit}` : data.apibaraQuota.remaining} />
                                        <QuotaRow label="Zużyto" value={data.apibaraQuota.used} />
                                        <QuotaRow label="Okres" value={data.apibaraQuota.periodStart && data.apibaraQuota.periodEnd
                                            ? `${formatDateTime(data.apibaraQuota.periodStart)} – ${formatDateTime(data.apibaraQuota.periodEnd)}` : null} />
                                        <QuotaRow label="Maks. lotów na stronę" value={data.apibaraQuota.perPageLimit} />
                                    </dl>
                                    <p className="text-xs text-avtovo-text-secondary">
                                        Stan z ostatniej odpowiedzi synchronizacji ({formatDateTime(data.apibaraQuota.observedAt)}); zapytania na żywo mogły go już zmniejszyć.
                                    </p>
                                </div>
                            ) : <p className="text-sm text-avtovo-text-secondary">Synchronizacja nie dostała jeszcze odpowiedzi z limitem zapytań.</p>}
                            {usage?.apibaraUsageError ? (
                                <p className="text-sm text-red-300">Nie udało się pobrać zużycia API ({usage.apibaraUsageError}).</p>
                            ) : usage ? (
                                <dl className="text-sm space-y-1.5 border-t border-avtovo-border pt-3">
                                    {usageEntries(usage.apibaraUsage).map(([k, v]) => (
                                        <div key={k} className="flex justify-between gap-3">
                                            <dt className="text-avtovo-text-secondary">{k}</dt><dd className="text-avtovo-text">{v}</dd>
                                        </div>
                                    ))}
                                </dl>
                            ) : (
                                <button className={actionButton} disabled={usageLoading} onClick={loadUsage}>
                                    <Gauge size={15} /> {usageLoading ? 'Pobieranie…' : 'Pobierz zużycie z Apibary (może zużyć 1 zapytanie)'}
                                </button>
                            )}
                        </div>
                    )}
                </section>
                <section className={card}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Kurs USD/PLN</h2>
                    {data.exchangeRate ? (
                        <div className="text-sm space-y-1">
                            <div className="text-2xl font-bold text-avtovo-text">{data.exchangeRate.rate}</div>
                            <div className="text-avtovo-text-secondary">pobrano {formatDateTime(data.exchangeRate.fetchedAt)}</div>
                            {data.exchangeRate.fallback && <Badge tone="yellow">Kurs awaryjny (API niedostępne)</Badge>}
                        </div>
                    ) : <p className="text-sm text-avtovo-text-secondary">Kurs nie był jeszcze pobierany od startu serwisu.</p>}
                </section>
                <section className={card}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Cennik W8 Shipping</h2>
                    <div className="text-sm space-y-1">
                        <div className="text-2xl font-bold text-avtovo-text">{data.w8PriceList}</div>
                        <div className="text-avtovo-text-secondary">{data.w8TowingRows} cen holowania</div>
                        {data.w8TowingRows === 0 && <Badge tone="yellow">Brak cen dla tego cennika — kalkulatory używają stawek ryczałtowych</Badge>}
                    </div>
                </section>
            </div>
            <h2 className="font-semibold text-avtovo-text mb-3">Ostatnie synchronizacje</h2>
            <DataTable columns={runColumns} rows={data.lastRuns} rowKey={r => r.id} loading={false} emptyText="Brak synchronizacji" />

            {dialog === 'sync' && (
                <ConfirmDialog
                    title="Synchronizuj aukcje"
                    message="Pobranie lotów z Apibary zużywa limit zapytań konta. Synchronizacja działa w tle."
                    confirmLabel="Synchronizuj"
                    requireReason={false}
                    onClose={() => setDialog(null)}
                    onConfirm={async () => {
                        await triggerAuctionSync();
                        toast.success('Synchronizacja została uruchomiona.');
                        reload();
                    }}
                />
            )}
            {dialog === 'reindex' && (
                <ConfirmDialog
                    title="Przeindeksuj wyszukiwarkę"
                    message="Wszystkie ogłoszenia zostaną ponownie wysłane do wyszukiwarki (Elasticsearch)."
                    confirmLabel="Przeindeksuj"
                    requireReason={false}
                    onClose={() => setDialog(null)}
                    onConfirm={async () => {
                        const result = await reindexSearch();
                        toast.success(`Wysłano ${result.published} ogłoszeń do przeindeksowania.`);
                    }}
                />
            )}
        </>
    );
}
