import { AlertTriangle, CheckCircle2, Circle, Download, FileText, Mail, Phone, Ship, User } from 'lucide-react';
import PhotoGallery from '../PhotoGallery';
import StatusBadge from './StatusBadge';
import ClientDocumentUpload from './ClientDocumentUpload';
import {
    MAIN_STATUSES,
    PHOTO_STAGE_LABELS,
    PHOTO_STAGES,
    SOURCE_LABELS,
    STATUS_LABELS,
    carTitle,
    categoryLabel,
    formatBytes,
    formatDate,
} from '../../constants/importOrders';
import type { ClientOrderView, DocumentCategory, ImportOrderStatus } from '../../types/importOrders';

const SHIPPING_LABELS: { key: keyof ClientOrderView['shipping']; label: string; date?: boolean }[] = [
    { key: 'paymentDeadline', label: 'Termin płatności na aukcji', date: true },
    { key: 'titleReceivedAt', label: 'Dokument własności otrzymany', date: true },
    { key: 'portOfLoading', label: 'Port załadunku' },
    { key: 'shippingLine', label: 'Armator' },
    { key: 'containerNumber', label: 'Numer kontenera' },
    { key: 'bookingNumber', label: 'Numer bookingu' },
    { key: 'billOfLading', label: 'Konosament (B/L)' },
    { key: 'vesselName', label: 'Statek' },
    { key: 'etd', label: 'Wypłynięcie (ETD)', date: true },
    { key: 'eta', label: 'Przypłynięcie (ETA)', date: true },
    { key: 'destinationPort', label: 'Port docelowy' },
    { key: 'customsClearedAt', label: 'Odprawa celna', date: true },
];

/**
 * The client page body, mobile first. Rendered on /zlecenie/:code and in the dealer's
 * "Podgląd jako klient", from the same allow-listed ClientOrderView. {@code onUpload} (client page only)
 * lets the client send their own documents, e.g. a payment confirmation.
 */
export default function ClientOrderViewCard({ view, onUpload }: {
    view: ClientOrderView;
    onUpload?: (files: File[], category: DocumentCategory) => Promise<void>;
}) {
    const title = carTitle(view);
    const cover = view.photos.find(p => p.url)?.url ?? null;
    const currentIndex = view.status ? MAIN_STATUSES.indexOf(view.status) : -1;
    // Date of each main step: the latest event with that status
    const stepDates = new Map<ImportOrderStatus, string>();
    for (const e of [...view.events].reverse()) stepDates.set(e.status, e.occurredAt);
    const shipping = SHIPPING_LABELS.filter(f => view.shipping[f.key]);

    return (
        <div className="space-y-4">
            {/* Header */}
            <section className="bg-avtovo-card border border-avtovo-border rounded-xl overflow-hidden">
                {cover && <img src={cover} alt={title} className="w-full aspect-[16/9] object-cover" />}
                <div className="p-4 sm:p-5 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge status={view.status} />
                        <span className="text-xs text-avtovo-text-secondary">{SOURCE_LABELS[view.source]}{view.lotNumber ? ` · lot ${view.lotNumber}` : ''}</span>
                    </div>
                    <h1 className="text-xl sm:text-2xl font-bold text-avtovo-text">{title}</h1>
                    {view.vin && <p className="text-sm text-avtovo-text-secondary break-all">VIN {view.vin}</p>}
                    {view.shipping.eta && (
                        <p className="flex items-center gap-2 text-sm text-avtovo-text">
                            <Ship size={16} className="text-avtovo-accent" />
                            Przewidywane przypłynięcie: <strong>{formatDate(view.shipping.eta)}</strong>
                        </p>
                    )}
                </div>
            </section>

            {(view.status === 'ON_HOLD' || view.status === 'CANCELLED') && (
                <div className={`flex gap-2 rounded-xl p-4 text-sm border ${view.status === 'CANCELLED'
                    ? 'bg-red-500/10 border-red-500/30 text-red-300' : 'bg-amber-500/10 border-amber-500/30 text-amber-300'}`}>
                    <AlertTriangle size={18} className="shrink-0" />
                    <p>Zlecenie jest obecnie: <strong>{STATUS_LABELS[view.status]}</strong>. Szczegóły u dealera.</p>
                </div>
            )}

            {/* Progress */}
            <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5">
                <h2 className="font-semibold text-avtovo-text mb-4">Postęp</h2>
                <ol className="space-y-0">
                    {MAIN_STATUSES.map((s, i) => {
                        const done = currentIndex >= 0 && i <= currentIndex;
                        const current = i === currentIndex;
                        return (
                            <li key={s} className="flex gap-3">
                                <div className="flex flex-col items-center">
                                    {done
                                        ? <CheckCircle2 size={20} className={current ? 'text-avtovo-accent' : 'text-emerald-400'} />
                                        : <Circle size={20} className="text-avtovo-border" />}
                                    {i < MAIN_STATUSES.length - 1 && (
                                        <span className={`w-px flex-1 min-h-4 ${done && i < currentIndex ? 'bg-emerald-400/60' : 'bg-avtovo-border'}`} />
                                    )}
                                </div>
                                <div className="pb-4 min-w-0">
                                    <p className={`text-sm ${current ? 'text-avtovo-text font-semibold' : done ? 'text-avtovo-text' : 'text-avtovo-text-secondary'}`}>
                                        {STATUS_LABELS[s]}
                                    </p>
                                    {stepDates.has(s) && <p className="text-xs text-avtovo-text-secondary">{formatDate(stepDates.get(s))}</p>}
                                </div>
                            </li>
                        );
                    })}
                </ol>
            </section>

            {/* Notes from the dealer */}
            {view.events.some(e => e.note) && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5">
                    <h2 className="font-semibold text-avtovo-text mb-3">Informacje od dealera</h2>
                    <ul className="space-y-3">
                        {view.events.filter(e => e.note).map((e, i) => (
                            <li key={i} className="border-l-2 border-avtovo-accent/50 pl-3">
                                <p className="text-xs text-avtovo-text-secondary">{formatDate(e.occurredAt)} · {STATUS_LABELS[e.status]}</p>
                                <p className="text-sm text-avtovo-text whitespace-pre-line">{e.note}</p>
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            {/* Shipping details */}
            {shipping.length > 0 && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5">
                    <h2 className="font-semibold text-avtovo-text mb-3">Szczegóły transportu</h2>
                    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
                        {shipping.map(f => (
                            <div key={f.key} className="flex justify-between gap-3 border-b border-avtovo-border/60 pb-1.5">
                                <dt className="text-avtovo-text-secondary">{f.label}</dt>
                                <dd className="text-avtovo-text text-right break-all">
                                    {f.date ? formatDate(view.shipping[f.key]) : view.shipping[f.key]}
                                </dd>
                            </div>
                        ))}
                    </dl>
                </section>
            )}

            {/* Title */}
            {(view.titleType || view.titleExportOk != null || view.titleRegistrationOk != null) && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5 text-sm space-y-1">
                    <h2 className="font-semibold text-avtovo-text mb-2">Dokument własności</h2>
                    {view.titleType && <p className="text-avtovo-text">{view.titleType}</p>}
                    {view.titleExportOk != null && <p className="text-avtovo-text-secondary">Eksport: {view.titleExportOk ? 'tak' : 'nie'}</p>}
                    {view.titleRegistrationOk != null && <p className="text-avtovo-text-secondary">Rejestracja: {view.titleRegistrationOk ? 'tak' : 'nie'}</p>}
                </section>
            )}

            {/* Documents */}
            {(view.documents.length > 0 || onUpload) && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5 space-y-4">
                    <h2 className="font-semibold text-avtovo-text">Dokumenty</h2>
                    {view.documents.length > 0 && <ul className="space-y-2">
                        {view.documents.map(d => (
                            <li key={d.id} className="flex items-center gap-3 bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2.5">
                                <FileText size={18} className="text-avtovo-accent shrink-0" />
                                <div className="min-w-0 flex-1">
                                    <p className="text-sm text-avtovo-text truncate">{d.originalName}</p>
                                    <p className="text-xs text-avtovo-text-secondary">
                                        {categoryLabel(d.category)} · {formatBytes(d.sizeBytes)}{d.uploadedByClient ? ' · wysłane przez Ciebie' : ''}
                                    </p>
                                </div>
                                {d.url && (
                                    <a href={d.url} target="_blank" rel="noreferrer"
                                       className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-avtovo-accent hover:bg-avtovo-accent-hover text-white text-sm shrink-0">
                                        <Download size={15} /> Pobierz
                                    </a>
                                )}
                            </li>
                        ))}
                    </ul>}
                    {onUpload && <ClientDocumentUpload onUpload={onUpload} />}
                </section>
            )}

            {/* Photos by stage */}
            {view.photos.some(p => p.url) && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5 space-y-4">
                    <h2 className="font-semibold text-avtovo-text">Zdjęcia</h2>
                    {PHOTO_STAGES.map(stage => {
                        const photos = view.photos.filter(p => p.category === stage && p.url)
                            .map(p => ({ thumb: p.url as string, large: p.url as string }));
                        if (photos.length === 0) return null;
                        return (
                            <div key={stage} className="space-y-2">
                                <h3 className="text-sm text-avtovo-text-secondary">{PHOTO_STAGE_LABELS[stage]} ({photos.length})</h3>
                                <PhotoGallery photos={photos} alt={`${title} — ${PHOTO_STAGE_LABELS[stage]}`} />
                            </div>
                        );
                    })}
                </section>
            )}

            {/* Dealer */}
            {(view.dealer.name || view.dealer.phone || view.dealer.email) && (
                <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5 space-y-2">
                    <h2 className="font-semibold text-avtovo-text">Kontakt z dealerem</h2>
                    {view.dealer.name && <p className="flex items-center gap-2 text-sm text-avtovo-text"><User size={15} /> {view.dealer.name}</p>}
                    {view.dealer.phone && (
                        <a href={`tel:${view.dealer.phone.replace(/\s+/g, '')}`} className="flex items-center gap-2 text-sm text-avtovo-accent">
                            <Phone size={15} /> {view.dealer.phone}
                        </a>
                    )}
                    {view.dealer.email && (
                        <a href={`mailto:${view.dealer.email}`} className="flex items-center gap-2 text-sm text-avtovo-accent break-all">
                            <Mail size={15} /> {view.dealer.email}
                        </a>
                    )}
                </section>
            )}
        </div>
    );
}
