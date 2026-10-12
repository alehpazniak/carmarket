import { useEffect, useMemo, useRef, useState } from 'react';
import { Check, Eye, EyeOff, Gavel, ImagePlus, Loader2, Trash2 } from 'lucide-react';
import {
    deleteImportOrderFile,
    fetchAuctionImage,
    getImportOrderFileUrl,
    updateImportOrderFile,
    uploadImportOrderFiles,
} from '../../../api/importOrders';
import { Field, Section, inputClass } from '../FormField';
import {
    MAX_AUCTION_PHOTOS,
    MAX_PHOTOS_PER_UPLOAD,
    MAX_PHOTO_BYTES,
    PHOTO_STAGES,
    PHOTO_STAGE_LABELS,
} from '../../../constants/importOrders';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import { auctionSnapshotPhotos } from '../../../utils/importOrderPrefill';
import { resizePhoto } from '../../../utils/imageResize';
import type { ImportOrderDetail, PhotoStage } from '../../../types/importOrders';

/** Zdjęcia: grouped by shipping stage; uploads are downscaled in the browser first. */
export default function PhotosTab({ order, onReload }: { order: ImportOrderDetail; onReload: () => void }) {
    const photos = useMemo(() => order.files.filter(f => f.kind === 'PHOTO'), [order.files]);
    const input = useRef<HTMLInputElement>(null);
    const [files, setFiles] = useState<File[]>([]);
    const [stage, setStage] = useState<PhotoStage>('US_WAREHOUSE');
    const [visible, setVisible] = useState(true);
    const [busy, setBusy] = useState(false);
    const [progress, setProgress] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [urls, setUrls] = useState<Record<string, string>>({});
    const [confirmId, setConfirmId] = useState<string | null>(null);

    // Presigned thumbnails for photos we don't have a URL for yet
    const missing = photos.filter(p => !urls[p.id]).map(p => p.id).join(',');
    useEffect(() => {
        if (!missing || !order.storageEnabled) return;
        Promise.all(missing.split(',').map(id => getImportOrderFileUrl(order.id, id)
            .then(u => [id, u.url] as const)
            .catch(() => null)))
            .then(results => setUrls(prev => ({
                ...prev,
                ...Object.fromEntries(results.filter((r): r is readonly [string, string] => r !== null)),
            })));
    }, [missing, order.id, order.storageEnabled]);

    if (!order.storageEnabled) {
        return <Section title="Zdjęcia"><p className="text-sm text-amber-300">Przechowywanie plików nie jest skonfigurowane na serwerze — zdjęcia są chwilowo niedostępne.</p></Section>;
    }

    const upload = async () => {
        if (files.length > MAX_PHOTOS_PER_UPLOAD) {
            setError(`Najwyżej ${MAX_PHOTOS_PER_UPLOAD} zdjęć naraz.`);
            return;
        }
        setBusy(true);
        setError(null);
        try {
            const resized: File[] = [];
            for (let i = 0; i < files.length; i++) {
                setProgress(`Zmniejszanie ${i + 1}/${files.length}…`);
                resized.push(await resizePhoto(files[i]));
            }
            const tooBig = resized.find(f => f.size > MAX_PHOTO_BYTES);
            if (tooBig) throw new Error(`${tooBig.name}: większe niż 15 MB`);
            setProgress('Wysyłanie…');
            await uploadImportOrderFiles(order.id, resized, 'PHOTO', stage, visible);
            setFiles([]);
            if (input.current) input.current.value = '';
            onReload();
        } catch (err) {
            setError(err instanceof Error && !('response' in err) ? err.message
                : apiErrorMessage(err, 'Nie udało się wysłać zdjęć (dozwolone: JPEG, PNG, WEBP).'));
        } finally {
            setBusy(false);
            setProgress(null);
        }
    };

    const run = async (action: () => Promise<unknown>, failure: string) => {
        setBusy(true);
        setError(null);
        try {
            await action();
            onReload();
        } catch (err) {
            setError(apiErrorMessage(err, failure));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="space-y-4">
            <Section title="Dodaj zdjęcia">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Field label={`Zdjęcia (JPEG, PNG, WEBP; najwyżej ${MAX_PHOTOS_PER_UPLOAD} naraz)`} hint="Przed wysłaniem zmniejszamy je do 2560 px (JPEG).">
                        <input ref={input} type="file" multiple accept="image/jpeg,image/png,image/webp"
                               onChange={e => setFiles(Array.from(e.target.files ?? []))}
                               className="block w-full text-sm text-avtovo-text-secondary file:mr-3 file:rounded-lg file:border-0 file:bg-avtovo-border file:px-3 file:py-2 file:text-avtovo-text" />
                    </Field>
                    <Field label="Etap">
                        <select value={stage} onChange={e => setStage(e.target.value as PhotoStage)} className={inputClass}>
                            {PHOTO_STAGES.map(s => <option key={s} value={s}>{PHOTO_STAGE_LABELS[s]}</option>)}
                        </select>
                    </Field>
                </div>
                <label className="flex items-center gap-2 text-sm text-avtovo-text">
                    <input type="checkbox" checked={visible} onChange={e => setVisible(e.target.checked)} />
                    Widoczne dla klienta
                </label>
                <div className="flex items-center gap-3">
                    <button onClick={upload} disabled={busy || files.length === 0}
                            className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-50">
                        {busy ? <Loader2 size={15} className="animate-spin" /> : <ImagePlus size={15} />} Wyślij {files.length > 0 ? `(${files.length})` : ''}
                    </button>
                    {progress && <span className="text-sm text-avtovo-text-secondary">{progress}</span>}
                </div>
            </Section>

            <AuctionPhotoImport order={order} onDone={onReload} />

            {error && <p className="text-sm text-red-400">{error}</p>}

            {photos.length === 0 && <Section title="Zdjęcia"><p className="text-sm text-avtovo-text-secondary">Brak zdjęć.</p></Section>}
            {PHOTO_STAGES.map(s => {
                const inStage = photos.filter(p => p.category === s);
                if (inStage.length === 0) return null;
                return (
                    <Section key={s} title={`${PHOTO_STAGE_LABELS[s]} (${inStage.length})`}>
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                            {inStage.map(p => (
                                <div key={p.id} className="bg-avtovo-bg border border-avtovo-border rounded-lg overflow-hidden">
                                    <a href={urls[p.id]} target="_blank" rel="noreferrer" className="block aspect-[4/3] bg-avtovo-card">
                                        {urls[p.id]
                                            ? <img src={urls[p.id]} alt={p.originalName} className="w-full h-full object-cover" loading="lazy" />
                                            : <div className="w-full h-full flex items-center justify-center"><Loader2 size={16} className="animate-spin text-avtovo-muted" /></div>}
                                    </a>
                                    <div className="flex items-center justify-between gap-1 p-2">
                                        <button disabled={busy}
                                                onClick={() => run(() => updateImportOrderFile(order.id, p.id, { clientVisible: !p.clientVisible }), 'Nie udało się zmienić widoczności.')}
                                                className={`inline-flex items-center gap-1 text-xs ${p.clientVisible ? 'text-emerald-400' : 'text-avtovo-text-secondary'}`}>
                                            {p.clientVisible ? <><Eye size={13} /> Widoczne</> : <><EyeOff size={13} /> Ukryte</>}
                                        </button>
                                        <select value={p.category} disabled={busy} aria-label="Etap"
                                                onChange={e => run(() => updateImportOrderFile(order.id, p.id, { category: e.target.value }), 'Nie udało się zmienić etapu.')}
                                                className="bg-transparent text-xs text-avtovo-text-secondary max-w-20">
                                            {PHOTO_STAGES.map(st => <option key={st} value={st}>{PHOTO_STAGE_LABELS[st]}</option>)}
                                        </select>
                                        {confirmId === p.id ? (
                                            <button onClick={() => run(() => deleteImportOrderFile(order.id, p.id), 'Nie udało się usunąć zdjęcia.')} className="text-xs text-red-400">Usuń?</button>
                                        ) : (
                                            <button onClick={() => setConfirmId(p.id)} aria-label="Usuń" className="text-avtovo-text-secondary hover:text-red-400"><Trash2 size={14} /></button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Section>
                );
            })}
        </div>
    );
}

/**
 * "Importuj zdjęcia z aukcji": thumbnails from the auction snapshot; the browser fetches the picked ones
 * through the logged-in image proxy (the auction sites block hotlinking) and uploads them as PHOTO/AUCTION.
 */
function AuctionPhotoImport({ order, onDone }: { order: ImportOrderDetail; onDone: () => void }) {
    const available = useMemo(() => auctionSnapshotPhotos(order.auctionSnapshot), [order.auctionSnapshot]);
    const [open, setOpen] = useState(false);
    const [selected, setSelected] = useState<number[]>([]);
    const [visible, setVisible] = useState(true);
    const [busy, setBusy] = useState(false);
    const [progress, setProgress] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    if (available.length === 0) return null;

    const toggle = (i: number) => setSelected(s => s.includes(i) ? s.filter(x => x !== i)
        : s.length >= MAX_AUCTION_PHOTOS ? s : [...s, i]);

    const importSelected = async () => {
        setBusy(true);
        setError(null);
        try {
            const blobs: Blob[] = [];
            for (let n = 0; n < selected.length; n++) {
                setProgress(`Pobieranie ${n + 1}/${selected.length}…`);
                blobs.push(await fetchAuctionImage(available[selected[n]].large));
            }
            setProgress('Wysyłanie…');
            const names = blobs.map((b, n) => `aukcja-${n + 1}.${b.type === 'image/png' ? 'png' : b.type === 'image/webp' ? 'webp' : 'jpg'}`);
            await uploadImportOrderFiles(order.id, blobs, 'PHOTO', 'AUCTION', visible, names);
            setSelected([]);
            setOpen(false);
            onDone();
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się zaimportować zdjęć z aukcji.'));
        } finally {
            setBusy(false);
            setProgress(null);
        }
    };

    return (
        <Section title="Zdjęcia z aukcji" actions={!open && (
            <button onClick={() => setOpen(true)} className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                <Gavel size={15} /> Importuj zdjęcia z aukcji
            </button>
        )}>
            {!open ? (
                <p className="text-sm text-avtovo-text-secondary">W danych aukcji jest {available.length} zdjęć.</p>
            ) : (
                <div className="space-y-3">
                    <p className="text-xs text-avtovo-muted">
                        Wybierz do {MAX_AUCTION_PHOTOS} zdjęć. Są pobierane przez proxy Apibara — według licznika /usage proxy nie zużywa limitu zapytań.
                    </p>
                    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                        {available.map((p, i) => {
                            const picked = selected.includes(i);
                            return (
                                <button key={i} type="button" onClick={() => toggle(i)}
                                        className={`relative aspect-[4/3] rounded-lg overflow-hidden border-2 ${picked ? 'border-avtovo-accent' : 'border-transparent'}`}>
                                    <img src={p.thumb} alt={`Zdjęcie z aukcji ${i + 1}`} className="w-full h-full object-cover" loading="lazy" />
                                    {picked && <span className="absolute top-1 right-1 bg-avtovo-accent rounded-full p-0.5"><Check size={12} className="text-white" /></span>}
                                </button>
                            );
                        })}
                    </div>
                    <label className="flex items-center gap-2 text-sm text-avtovo-text">
                        <input type="checkbox" checked={visible} onChange={e => setVisible(e.target.checked)} />
                        Widoczne dla klienta
                    </label>
                    <div className="flex flex-wrap items-center gap-3">
                        <button onClick={importSelected} disabled={busy || selected.length === 0}
                                className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-50">
                            {busy && <Loader2 size={15} className="animate-spin" />} Importuj wybrane ({selected.length})
                        </button>
                        <button onClick={() => setSelected(available.slice(0, MAX_AUCTION_PHOTOS).map((_, i) => i))} className="text-sm text-avtovo-text-secondary hover:text-avtovo-text">
                            Zaznacz {Math.min(MAX_AUCTION_PHOTOS, available.length)}
                        </button>
                        <button onClick={() => setOpen(false)} className="text-sm text-avtovo-text-secondary hover:text-avtovo-text">Anuluj</button>
                        {progress && <span className="text-sm text-avtovo-text-secondary">{progress}</span>}
                    </div>
                    {error && <p className="text-sm text-red-400">{error}</p>}
                </div>
            )}
        </Section>
    );
}
