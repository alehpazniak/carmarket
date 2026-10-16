import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Archive, ClipboardList, ExternalLink, Loader2, Plus, Search } from 'lucide-react';
import { listImportOrders } from '../../api/importOrders';
import OrderThumbnail from '../../components/importOrders/OrderThumbnail';
import StatusBadge from '../../components/importOrders/StatusBadge';
import { ALL_STATUSES, SOURCE_LABELS, STATUS_LABELS, auctionLink, carTitle, formatDate } from '../../constants/importOrders';
import type { Page } from '../../types';
import type { ImportOrderStatus, ImportOrderSummary } from '../../types/importOrders';

/** /panel-dealera/zlecenia — the dealer's import orders. */
export default function ImportOrderList() {
    const [status, setStatus] = useState<ImportOrderStatus | ''>('');
    const [q, setQ] = useState('');
    const [query, setQuery] = useState('');
    const [archived, setArchived] = useState(false);
    const [page, setPage] = useState(0);
    const [data, setData] = useState<Page<ImportOrderSummary> | null>(null);
    const [error, setError] = useState(false);

    const load = useCallback(() => {
        listImportOrders({ status, q: query, archived, page, size: 24 })
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
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {data.content.map(o => {
                                const title = carTitle(o);
                                const link = auctionLink(o);
                                return (
                                    <div key={o.id} className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden flex flex-col hover:border-avtovo-accent/60 transition-colors">
                                        <Link to={`/panel-dealera/zlecenia/${o.id}`} className="block">
                                            <div className="relative aspect-[4/3] bg-avtovo-bg">
                                                <OrderThumbnail thumbnailUrl={o.thumbnailUrl} auctionPhotoUrl={o.auctionPhotoUrl} alt={title} />
                                                <div className="absolute top-2 left-2"><StatusBadge status={o.status} /></div>
                                            </div>
                                            <div className="p-4 space-y-1">
                                                <h2 className="font-semibold text-avtovo-text truncate">{title}</h2>
                                                <p className="text-xs text-avtovo-text-secondary">
                                                    {SOURCE_LABELS[o.source]}{o.lotNumber ? ` · lot ${o.lotNumber}` : ''}
                                                </p>
                                                <p className="text-xs text-avtovo-text-secondary font-mono break-all">{o.vin ?? 'VIN —'}</p>
                                                <dl className="grid grid-cols-2 gap-x-3 gap-y-1 pt-2 text-xs">
                                                    <dt className="text-avtovo-muted">Klient</dt>
                                                    <dd className="text-avtovo-text truncate">{o.clientName ?? '—'}</dd>
                                                    <dt className="text-avtovo-muted">ETA</dt>
                                                    <dd className="text-avtovo-text">{formatDate(o.eta)}</dd>
                                                    {o.containerNumber && <>
                                                        <dt className="text-avtovo-muted">Kontener</dt>
                                                        <dd className="text-avtovo-text font-mono">{o.containerNumber}</dd>
                                                    </>}
                                                </dl>
                                            </div>
                                        </Link>
                                        {link && (
                                            <a href={link} target="_blank" rel="noopener noreferrer"
                                               className="mt-auto mx-4 mb-4 inline-flex items-center gap-1.5 text-xs text-avtovo-accent hover:underline">
                                                <ExternalLink size={13} /> Zobacz na {o.source === 'OTHER' ? 'aukcji' : SOURCE_LABELS[o.source]}
                                            </a>
                                        )}
                                    </div>
                                );
                            })}
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
