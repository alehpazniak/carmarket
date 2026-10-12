import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink, ShieldCheck, ShieldOff, Trash2 } from 'lucide-react';
import { blockListing, getAdminListing, removeListingImage, unblockListing, type ReportView } from '../api';
import { Badge } from '../components/Badges';
import ConfirmDialog from '../components/ConfirmDialog';
import DataTable, { type Column } from '../components/DataTable';
import ListingStatusBadge from '../components/ListingStatusBadge';
import PageHeader from '../components/PageHeader';
import { ErrorState, LoadingState } from '../components/States';
import { useToast } from '../components/Toasts';
import { useLoader } from '../hooks';
import { formatDateTime } from '../labels';
import { REPORT_REASON_LABELS, type ReportReason } from '../../constants/reports';

const card = 'bg-avtovo-card border border-avtovo-border rounded-xl p-5';
const actionButton = 'inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text hover:bg-white/5';

const REPORT_STATUS: Record<ReportView['status'], { label: string; tone: 'red' | 'gray' | 'green' }> = {
    OPEN: { label: 'Otwarte', tone: 'red' },
    RESOLVED: { label: 'Rozpatrzone (blokada)', tone: 'green' },
    DISMISSED: { label: 'Odrzucone', tone: 'gray' },
};

const reportColumns: Column<ReportView>[] = [
    { key: 'when', header: 'Kiedy', render: r => <span className="whitespace-nowrap">{formatDateTime(r.createdAt)}</span> },
    { key: 'reason', header: 'Powód', render: r => REPORT_REASON_LABELS[r.reason as ReportReason] ?? r.reason },
    { key: 'comment', header: 'Komentarz', render: r => <span className="text-avtovo-text-secondary whitespace-pre-line">{r.comment ?? '—'}</span> },
    { key: 'reporter', header: 'Zgłaszający', render: r => <Link to={`/admin/uzytkownicy/${r.reporterId}`} className="text-avtovo-accent hover:underline text-xs">{r.reporterId.slice(0, 8)}…</Link> },
    { key: 'status', header: 'Stan', render: r => <Badge tone={REPORT_STATUS[r.status].tone}>{REPORT_STATUS[r.status].label}</Badge> },
];

type Dialog = { kind: 'block' | 'unblock' } | { kind: 'image'; url: string } | null;

/** /admin/ogloszenia/:id */
export default function ListingDetailPage() {
    const { id = '' } = useParams();
    const toast = useToast();
    const loader = useCallback(() => getAdminListing(id), [id]);
    const { data, error, loading, reload } = useLoader(loader);
    const [dialog, setDialog] = useState<Dialog>(null);

    if (loading) return <LoadingState />;
    if (error || !data) return <ErrorState label="Nie udało się wczytać ogłoszenia." onRetry={reload} />;
    const car = data.listing;
    const images = car.imageUrls ?? [];
    const publicVisible = car.status === 'ACTIVE' || car.status === 'SOLD';

    return (
        <>
            <Link to="/admin/ogloszenia" className="inline-flex items-center gap-1 text-sm text-avtovo-text-secondary hover:text-avtovo-text mb-3">
                <ArrowLeft size={14} /> Ogłoszenia
            </Link>
            <PageHeader
                title={`${car.make} ${car.model} ${car.year}`}
                subtitle={`${Number(car.price).toLocaleString('pl-PL')} zł · ${car.city ?? '—'}`}
                actions={
                    <>
                        {publicVisible && (
                            <a href={`/ogloszenia/${car.id}`} target="_blank" rel="noreferrer" className={actionButton}>
                                <ExternalLink size={15} /> Strona ogłoszenia
                            </a>
                        )}
                        {car.status === 'BLOCKED' ? (
                            <button className={actionButton} onClick={() => setDialog({ kind: 'unblock' })}>
                                <ShieldCheck size={15} className="text-green-400" /> Odblokuj
                            </button>
                        ) : car.status !== 'REMOVED' && (
                            <button className={actionButton} onClick={() => setDialog({ kind: 'block' })}>
                                <ShieldOff size={15} className="text-red-400" /> Zablokuj
                            </button>
                        )}
                    </>
                }
            />

            <div className="grid gap-4 lg:grid-cols-3 mb-4">
                <section className={`${card} lg:col-span-2`}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Zdjęcia ({images.length})</h2>
                    {images.length === 0 ? <p className="text-sm text-avtovo-text-secondary">Brak zdjęć</p> : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                            {images.map(url => (
                                <div key={url} className="relative group">
                                    <img src={url} alt="" className="w-full aspect-[4/3] object-cover rounded-lg bg-avtovo-bg" />
                                    {url === car.primaryImageUrl && (
                                        <span className="absolute top-1.5 left-1.5 bg-black/70 text-white text-[10px] px-1.5 py-0.5 rounded">Główne</span>
                                    )}
                                    <button onClick={() => setDialog({ kind: 'image', url })} aria-label="Usuń zdjęcie"
                                            className="absolute top-1.5 right-1.5 p-1.5 rounded-md bg-black/70 text-red-300 hover:text-red-200">
                                        <Trash2 size={14} />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </section>
                <section className={card}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Szczegóły</h2>
                    <dl className="text-sm space-y-2">
                        <div className="flex justify-between gap-3"><dt className="text-avtovo-text-secondary">Status</dt><dd><ListingStatusBadge status={car.status} /></dd></div>
                        {car.moderationReason && (
                            <div><dt className="text-avtovo-text-secondary">Powód blokady</dt><dd className="text-avtovo-text mt-0.5">{car.moderationReason}</dd></div>
                        )}
                        {data.moderatedAt && (
                            <div className="flex justify-between gap-3"><dt className="text-avtovo-text-secondary">Ostatnia moderacja</dt><dd>{formatDateTime(data.moderatedAt)}</dd></div>
                        )}
                        <div className="flex justify-between gap-3">
                            <dt className="text-avtovo-text-secondary">Sprzedawca</dt>
                            <dd><Link to={`/admin/uzytkownicy/${car.sellerId}`} className="text-avtovo-accent hover:underline">profil</Link></dd>
                        </div>
                        <div className="flex justify-between gap-3"><dt className="text-avtovo-text-secondary">Dodano</dt><dd>{formatDateTime(car.createdAt)}</dd></div>
                        <div className="flex justify-between gap-3">
                            <dt className="text-avtovo-text-secondary">Dziennik</dt>
                            <dd><Link to={`/admin/dziennik?targetType=LISTING&targetId=${car.id}`} className="text-avtovo-accent hover:underline">zdarzenia</Link></dd>
                        </div>
                    </dl>
                    {car.description && <p className="text-sm text-avtovo-text-secondary mt-4 whitespace-pre-line line-clamp-[12]">{car.description}</p>}
                </section>
            </div>

            <h2 className="font-semibold text-avtovo-text mb-3">Historia zgłoszeń</h2>
            <DataTable columns={reportColumns} rows={data.reports} rowKey={r => r.id} loading={false} emptyText="Brak zgłoszeń" />

            {dialog?.kind === 'block' && (
                <ConfirmDialog
                    title="Zablokuj ogłoszenie"
                    message="Ogłoszenie zniknie z wyszukiwarki, otwarte zgłoszenia zostaną zamknięte, a sprzedawca dostanie e-mail z powodem."
                    reasonLabel="Powód (sprzedawca zobaczy go w e-mailu i w „Moich ogłoszeniach”)"
                    confirmLabel="Zablokuj"
                    destructive
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await blockListing(car.id, reason);
                        toast.success('Ogłoszenie zostało zablokowane.');
                        reload();
                    }}
                />
            )}
            {dialog?.kind === 'unblock' && (
                <ConfirmDialog
                    title="Odblokuj ogłoszenie"
                    message="Ogłoszenie wróci do wyszukiwarki jako aktywne; sprzedawca dostanie e-mail."
                    confirmLabel="Odblokuj"
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await unblockListing(car.id, reason);
                        toast.success('Ogłoszenie zostało odblokowane.');
                        reload();
                    }}
                />
            )}
            {dialog?.kind === 'image' && (
                <ConfirmDialog
                    title="Usuń zdjęcie"
                    message="Zdjęcie zostanie trwale usunięte z ogłoszenia i z magazynu plików."
                    confirmLabel="Usuń zdjęcie"
                    destructive
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await removeListingImage(car.id, dialog.url, reason);
                        toast.success('Zdjęcie zostało usunięte.');
                        reload();
                    }}
                >
                    <img src={dialog.url} alt="" className="w-full max-h-48 object-contain rounded-lg bg-avtovo-bg" />
                </ConfirmDialog>
            )}
        </>
    );
}
