import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
    Activity, AlertTriangle, BellRing, Car, ClipboardList, CreditCard, Gavel, Loader2, MessageSquare, Users,
} from 'lucide-react';
import {
    getAuctionStatus, getChatStats, getImportOrderStats, getListingStats, getNotificationStats, getPaymentStats,
    getSystemHealth, getUserStats,
} from '../api';
import BarChart from '../components/BarChart';
import PageHeader from '../components/PageHeader';
import { useLoader } from '../hooks';
import { formatDateTime, formatMoney } from '../labels';

/**
 * One tile per service, each loading on its own: a service that is down only blanks its own tile
 * ("Dane niedostępne"), the rest of the dashboard keeps working.
 */
function Tile<T>({ title, icon, to, loader, children }: {
    title: string;
    icon: ReactNode;
    /** Section the tile opens; omitted when there is none (e.g. chat, statistics only). */
    to?: string;
    loader: () => Promise<T>;
    children: (data: T) => ReactNode;
}) {
    const { data, error, loading } = useLoader(loader);
    const body = (
        <>
            <div className="flex items-center gap-2 text-sm text-avtovo-text-secondary mb-3">{icon} {title}</div>
            {loading ? <Loader2 size={18} className="animate-spin text-avtovo-accent" />
                : error || data === undefined ? (
                    <p className="flex items-center gap-1.5 text-sm text-avtovo-muted"><AlertTriangle size={14} /> Dane niedostępne</p>
                ) : children(data)}
        </>
    );
    const className = 'block bg-avtovo-card border border-avtovo-border rounded-xl p-4';
    return to ? <Link to={to} className={`${className} hover:border-gray-600 transition-colors`}>{body}</Link>
        : <div className={className}>{body}</div>;
}

/** One request per mount, shared by the tile and the chart that show the same figures. */
function useShared<T>(fetch: () => Promise<T>): () => Promise<T> {
    const [loader] = useState(() => {
        let promise: Promise<T> | null = null;
        return () => (promise ??= fetch());
    });
    return loader;
}

function Big({ value, hint }: { value: ReactNode; hint?: ReactNode }) {
    return (
        <div>
            <div className="text-2xl font-bold text-avtovo-text">{value}</div>
            {hint && <div className="text-xs text-avtovo-text-secondary mt-1">{hint}</div>}
        </div>
    );
}

function ChartCard<T>({ loader, render }: { loader: () => Promise<T>; render: (data: T) => ReactNode }) {
    const { data, error, loading } = useLoader(loader);
    return (
        <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 min-h-[10rem]">
            {loading ? <Loader2 size={18} className="animate-spin text-avtovo-accent" />
                : error || data === undefined ? <p className="text-sm text-avtovo-muted">Dane niedostępne</p>
                    : render(data)}
        </div>
    );
}

const money = (grosz: number) => formatMoney(grosz);

/** /admin — Pulpit. */
export default function DashboardPage() {
    // Every tile loads in parallel; a tile and its chart share one request
    const users = useShared(getUserStats);
    const listings = useShared(getListingStats);
    const payments = useShared(() => getPaymentStats());
    const orders = useShared(getImportOrderStats);
    const chat = useShared(getChatStats);
    const notifications = useShared(getNotificationStats);
    const auctions = useShared(getAuctionStatus);
    const system = useShared(getSystemHealth);

    return (
        <>
            <PageHeader title="Pulpit" />
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4 mb-4">
                <Tile title="Użytkownicy" icon={<Users size={16} />} to="/admin/uzytkownicy" loader={users}>
                    {s => <Big value={s.total} hint={`+${s.newLast7Days} w 7 dni · zablokowani: ${s.blocked} · prośby dealera: ${s.pendingDealerRequests}`} />}
                </Tile>
                <Tile title="Ogłoszenia" icon={<Car size={16} />} to="/admin/ogloszenia" loader={listings}>
                    {s => <Big value={s.byStatus.ACTIVE ?? 0} hint={<>aktywne · +{s.newLast7Days} w 7 dni · <span className={s.openReports ? 'text-red-400' : ''}>zgłoszenia: {s.openReports}</span></>} />}
                </Tile>
                <Tile title="Płatności (30 dni)" icon={<CreditCard size={16} />} to="/admin/platnosci" loader={payments}>
                    {s => <Big value={money(s.revenue)} hint={`zwroty: ${s.refundedCount} (${money(s.refundedAmount)})`} />}
                </Tile>
                <Tile title="Zlecenia importu" icon={<ClipboardList size={16} />} to="/admin/zlecenia" loader={orders}>
                    {s => <Big value={s.open} hint={`otwarte · +${s.newLast30Days} w 30 dni · dealerzy: ${s.dealers}`} />}
                </Tile>
                <Tile title="Wiadomości" icon={<MessageSquare size={16} />} loader={chat}>
                    {s => <Big value={s.messagesLast7Days} hint={`wiadomości w 7 dni · nowe rozmowy: ${s.newConversationsLast7Days}`} />}
                </Tile>
                <Tile title="Powiadomienia" icon={<BellRing size={16} />} to="/admin/powiadomienia" loader={notifications}>
                    {s => <Big value={<span className={s.byStatus.FAILED ? 'text-red-400' : ''}>{s.byStatus.FAILED ?? 0}</span>}
                                hint={`nieudane · ostatnie 24 h: ${s.failedLast24h} · w kolejce: ${s.pending}`} />}
                </Tile>
                <Tile title="Aukcje" icon={<Gavel size={16} />} to="/admin/aukcje" loader={auctions}>
                    {s => {
                        const last = s.lastRuns.reduce<typeof s.lastRuns[number] | null>(
                            (a, r) => !a || r.startedAt > a.startedAt ? r : a, null);
                        return <Big value={s.apibaraConfigured ? (s.syncRunning ? 'Synchronizacja…' : 'Apibara OK') : 'Brak klucza'}
                                    hint={last ? `ostatnia synchronizacja ${formatDateTime(last.startedAt)}${last.error ? ' (błąd)' : ''}` : 'brak synchronizacji'} />;
                    }}
                </Tile>
                <Tile title="System" icon={<Activity size={16} />} to="/admin/system" loader={system}>
                    {s => {
                        const down = s.instances.filter(i => i.status !== 'UP');
                        return <Big value={<span className={down.length ? 'text-red-400' : 'text-green-400'}>{down.length ? `${down.length} DOWN` : 'Wszystko UP'}</span>}
                                    hint={down.length ? down.map(d => d.service).join(', ') : `${s.instances.length} instancji`} />;
                    }}
                </Tile>
            </div>
            <div className="grid gap-3 lg:grid-cols-3">
                <ChartCard loader={users} render={s => (
                    <BarChart label="Nowi użytkownicy (30 dni)" points={s.daily.map(d => ({ date: d.date, value: d.count }))} />
                )} />
                <ChartCard loader={listings} render={s => (
                    <BarChart label="Nowe ogłoszenia (30 dni)" points={s.daily.map(d => ({ date: d.date, value: d.count }))} />
                )} />
                <ChartCard loader={payments} render={s => (
                    <BarChart label="Przychód (30 dni)" format={money} points={s.daily.map(d => ({ date: d.date, value: d.amount }))} />
                )} />
            </div>
        </>
    );
}
