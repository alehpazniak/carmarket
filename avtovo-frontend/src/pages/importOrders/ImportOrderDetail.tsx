import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Archive, ArchiveRestore, ChevronLeft, Loader2, Trash2 } from 'lucide-react';
import { deleteImportOrder, getImportOrder, setImportOrderArchived } from '../../api/importOrders';
import StatusBadge from '../../components/importOrders/StatusBadge';
import OverviewTab from '../../components/importOrders/tabs/OverviewTab';
import StatusTab from '../../components/importOrders/tabs/StatusTab';
import TransportTab from '../../components/importOrders/tabs/TransportTab';
import DocumentsTab from '../../components/importOrders/tabs/DocumentsTab';
import PhotosTab from '../../components/importOrders/tabs/PhotosTab';
import ClientAccessTab from '../../components/importOrders/tabs/ClientAccessTab';
import NotesTab from '../../components/importOrders/tabs/NotesTab';
import { SOURCE_LABELS, carTitle } from '../../constants/importOrders';
import { apiErrorMessage } from '../../utils/importOrderUi';
import type { ImportOrderDetail as Order } from '../../types/importOrders';

const TABS = [
    { id: 'overview', label: 'Przegląd' },
    { id: 'status', label: 'Status' },
    { id: 'transport', label: 'Transport' },
    { id: 'documents', label: 'Dokumenty' },
    { id: 'photos', label: 'Zdjęcia' },
    { id: 'access', label: 'Dostęp klienta' },
    { id: 'notes', label: 'Notatki wewnętrzne' },
] as const;

type TabId = typeof TABS[number]['id'];

/** /panel-dealera/zlecenia/:id */
export default function ImportOrderDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [order, setOrder] = useState<Order | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [tab, setTab] = useState<TabId>('overview');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const [busy, setBusy] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const load = useCallback(() => {
        if (!id) return;
        getImportOrder(id)
            .then(o => {
                setOrder(o);
                setLoadError(null);
            })
            .catch(err => setLoadError(apiErrorMessage(err, 'Nie udało się wczytać zlecenia.')));
    }, [id]);

    useEffect(() => {
        load();
    }, [load]);

    if (loadError) {
        return (
            <div className="min-h-screen bg-avtovo-bg py-16 text-center space-y-3">
                <p className="text-avtovo-text-secondary">{loadError}</p>
                <Link to="/panel-dealera/zlecenia" className="text-avtovo-accent text-sm hover:underline">Wróć do listy</Link>
            </div>
        );
    }
    if (!order) {
        return <div className="min-h-screen bg-avtovo-bg py-16 flex justify-center"><Loader2 className="animate-spin text-avtovo-accent" /></div>;
    }

    const toggleArchive = async () => {
        setBusy(true);
        setActionError(null);
        try {
            setOrder(await setImportOrderArchived(order.id, !order.archivedAt));
        } catch (err) {
            setActionError(apiErrorMessage(err));
        } finally {
            setBusy(false);
        }
    };

    const remove = async () => {
        setBusy(true);
        setActionError(null);
        try {
            await deleteImportOrder(order.id);
            navigate('/panel-dealera/zlecenia', { replace: true });
        } catch (err) {
            setActionError(apiErrorMessage(err, 'Nie udało się usunąć zlecenia.'));
            setBusy(false);
        }
    };

    return (
        <div className="min-h-screen bg-avtovo-bg py-6 sm:py-8">
            <div className="max-w-5xl mx-auto px-4 space-y-4">
                <Link to="/panel-dealera/zlecenia" className="inline-flex items-center gap-1 text-avtovo-text-secondary hover:text-avtovo-text text-sm">
                    <ChevronLeft size={16} /> Zlecenia importu
                </Link>

                <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                            <StatusBadge status={order.status} />
                            {order.archivedAt && <span className="text-xs px-2 py-1 rounded-md bg-avtovo-border text-avtovo-text-secondary">Zarchiwizowane</span>}
                            {!order.access.enabled && <span className="text-xs px-2 py-1 rounded-md bg-amber-500/15 text-amber-400">Dostęp klienta wyłączony</span>}
                        </div>
                        <h1 className="text-2xl font-bold text-avtovo-text">{carTitle(order)}</h1>
                        <p className="text-sm text-avtovo-text-secondary break-all">
                            {SOURCE_LABELS[order.source]}{order.lotNumber ? ` · lot ${order.lotNumber}` : ''}{order.vin ? ` · VIN ${order.vin}` : ''}
                            {order.clientName ? ` · ${order.clientName}` : ''}
                        </p>
                    </div>
                    <div className="flex items-center gap-2">
                        <button onClick={toggleArchive} disabled={busy}
                                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text-secondary hover:text-avtovo-text disabled:opacity-50">
                            {order.archivedAt ? <><ArchiveRestore size={15} /> Przywróć</> : <><Archive size={15} /> Archiwizuj</>}
                        </button>
                        {confirmDelete ? (
                            <>
                                <button onClick={remove} disabled={busy}
                                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-red-500 hover:bg-red-600 text-white text-sm disabled:opacity-50">
                                    <Trash2 size={15} /> Usuń na zawsze
                                </button>
                                <button onClick={() => setConfirmDelete(false)} className="px-3 py-2 text-sm text-avtovo-text-secondary">Anuluj</button>
                            </>
                        ) : (
                            <button onClick={() => setConfirmDelete(true)}
                                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-avtovo-border text-sm text-red-400 hover:border-red-500/50">
                                <Trash2 size={15} /> Usuń
                            </button>
                        )}
                    </div>
                </div>
                {confirmDelete && (
                    <p className="text-sm text-red-300">Usunięcie skasuje zlecenie, historię i wszystkie pliki (także w chmurze). Tego nie można cofnąć.</p>
                )}
                {actionError && <p className="text-sm text-red-400">{actionError}</p>}

                <nav className="flex gap-1 overflow-x-auto border-b border-avtovo-border -mx-4 px-4">
                    {TABS.map(t => (
                        <button key={t.id} onClick={() => setTab(t.id)}
                                className={`px-3 py-2 text-sm whitespace-nowrap border-b-2 -mb-px transition-colors ${tab === t.id
                                    ? 'border-avtovo-accent text-avtovo-text' : 'border-transparent text-avtovo-text-secondary hover:text-avtovo-text'}`}>
                            {t.label}
                        </button>
                    ))}
                </nav>

                {tab === 'overview' && <OverviewTab order={order} onChange={setOrder} />}
                {tab === 'status' && <StatusTab order={order} onChange={setOrder} />}
                {tab === 'transport' && <TransportTab order={order} onChange={setOrder} />}
                {tab === 'documents' && <DocumentsTab order={order} onReload={load} />}
                {tab === 'photos' && <PhotosTab order={order} onReload={load} />}
                {tab === 'access' && <ClientAccessTab order={order} onChange={setOrder} />}
                {tab === 'notes' && <NotesTab order={order} onChange={setOrder} />}
            </div>
        </div>
    );
}
