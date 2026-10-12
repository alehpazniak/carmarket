import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Archive, ClipboardList, Loader2, Plus, Search } from 'lucide-react';
import { listImportOrders } from '../../api/importOrders';
import StatusBadge from '../../components/importOrders/StatusBadge';
import { ALL_STATUSES, SOURCE_LABELS, STATUS_LABELS, carTitle, formatDate } from '../../constants/importOrders';
import type { Page } from '../../types';
import type { ImportOrderStatus, ImportOrderSummary } from '../../types/importOrders';

/** /panel-dealera/zlecenia — the dealer's import orders. */
export default function ImportOrderList() {
    const navigate = useNavigate();
    const [status, setStatus] = useState<ImportOrderStatus | ''>('');
    const [q, setQ] = useState('');
    const [query, setQuery] = useState('');
    const [archived, setArchived] = useState(false);
    const [page, setPage] = useState(0);
    const [data, setData] = useState<Page<ImportOrderSummary> | null>(null);
    const [error, setError] = useState(false);

    const load = useCallback(() => {
        listImportOrders({ status, q: query, archived, page, size: 20 })
            .then(result => {
                setData(result);
                setError(false);
            })
            .catch(err => {
                console.error(err);
                setError(true);
            });
    }, [status, query, archived, page]);

    useEffect(() => {
        load();
    }, [load]);

    // Debounce the search box
    useEffect(() => {
        const t = setTimeout(() => {
            setQuery(q.trim());
            setPage(0);
        }, 300);
        return () => clearTimeout(t);
    }, [q]);

    return (
        <div className="min-h-screen bg-avtovo-bg py-6 sm:py-8">
            <div className="max-w-6xl mx-auto px-4 space-y-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                        <ClipboardList size={22} className="text-avtovo-accent" />
                        <h1 className="text-2xl font-bold text-avtovo-text">Zlecenia importu</h1>
                    </div>
                    <Link
                        to="/panel-dealera/zlecenia/nowe"
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2 rounded-lg text-sm font-medium"
                    >
                        <Plus size={16} /> Nowe zlecenie
                    </Link>
                </div>

                <div className="flex flex-col sm:flex-row gap-2">
                    <label className="relative flex-1">
                        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-avtovo-text-secondary" />
                        <input
                            value={q}
                            onChange={e => setQ(e.target.value)}
                            placeholder="Szukaj: VIN, lot, klient, marka, model"
                            className="w-full bg-avtovo-card border border-avtovo-border rounded-lg pl-9 pr-3 py-2 text-sm text-avtovo-text placeholder:text-avtovo-muted focus:outline-none focus:border-avtovo-accent"
                        />
                    </label>
                    <select
                        value={status}
                        onChange={e => {
                            setStatus(e.target.value as ImportOrderStatus | '');
                            setPage(0);
                        }}
                        className="bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text"
                    >
                        <option value="">Wszystkie statusy</option>
                        {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
                    </select>
                    <button
                        onClick={() => {
                            setArchived(a => !a);
                            setPage(0);
                        }}
                        className={`inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-sm border ${archived
                            ? 'border-avtovo-accent text-avtovo-accent' : 'border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text'}`}
                    >
                        <Archive size={15} /> {archived ? 'Archiwum' : 'Aktywne'}
                    </button>
                </div>

                {error && <p className="text-red-400 text-sm">Nie udało się wczytać zleceń. <button onClick={load} className="underline">Spróbuj ponownie</button></p>}
                {!data && !error && <Loader2 className="animate-spin text-avtovo-accent mx-auto" />}

                {data && data.content.length === 0 && (
                    <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-8 text-center text-avtovo-text-secondary">
                        {query || status || archived ? 'Brak zleceń dla tych filtrów.' : 'Nie masz jeszcze zleceń. Utwórz pierwsze — np. ze strony pojazdu w zakładce Aukcje.'}
                    </div>
                )}

                {data && data.content.length > 0 && (
                    <>
                        {/* Mobile: cards */}
                        <div className="sm:hidden space-y-2">
                            {data.content.map(o => (
                                <button key={o.id} onClick={() => navigate(`/panel-dealera/zlecenia/${o.id}`)}
                                        className="w-full text-left bg-avtovo-card border border-avtovo-border rounded-xl p-4 space-y-1.5">
                                    <div className="flex items-center justify-between gap-2">
                                        <span className="font-medium text-avtovo-text truncate">{carTitle(o)}</span>
                                        <StatusBadge status={o.status} />
                                    </div>
                                    <p className="text-xs text-avtovo-text-secondary break-all">{o.vin ?? '—'}</p>
                                    <p className="text-xs text-avtovo-text-secondary">{o.clientName ?? 'Bez klienta'} · ETA {formatDate(o.eta)}</p>
                                </button>
                            ))}
                        </div>
                        {/* Desktop: table */}
                        <div className="hidden sm:block bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden">
                            <table className="w-full text-sm">
                                <thead className="text-left text-avtovo-text-secondary border-b border-avtovo-border">
                                <tr>
                                    <th className="px-4 py-3 font-medium">Status</th>
                                    <th className="px-4 py-3 font-medium">Pojazd</th>
                                    <th className="px-4 py-3 font-medium">VIN</th>
                                    <th className="px-4 py-3 font-medium">Klient</th>
                                    <th className="px-4 py-3 font-medium">ETA</th>
                                </tr>
                                </thead>
                                <tbody>
                                {data.content.map(o => (
                                    <tr key={o.id} onClick={() => navigate(`/panel-dealera/zlecenia/${o.id}`)}
                                        className="border-b border-avtovo-border/60 last:border-0 hover:bg-white/5 cursor-pointer">
                                        <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
                                        <td className="px-4 py-3 text-avtovo-text">
                                            {carTitle(o)}
                                            <span className="block text-xs text-avtovo-text-secondary">{SOURCE_LABELS[o.source]}{o.lotNumber ? ` · lot ${o.lotNumber}` : ''}</span>
                                        </td>
                                        <td className="px-4 py-3 text-avtovo-text-secondary font-mono text-xs">{o.vin ?? '—'}</td>
                                        <td className="px-4 py-3 text-avtovo-text">{o.clientName ?? '—'}</td>
                                        <td className="px-4 py-3 text-avtovo-text-secondary">{formatDate(o.eta)}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                        {data.totalPages > 1 && (
                            <div className="flex items-center justify-center gap-3 text-sm">
                                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                                        className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text disabled:opacity-40">Poprzednia</button>
                                <span className="text-avtovo-text-secondary">{page + 1} / {data.totalPages}</span>
                                <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}
                                        className="px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text disabled:opacity-40">Następna</button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
