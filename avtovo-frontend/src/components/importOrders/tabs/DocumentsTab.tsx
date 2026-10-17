import { useRef, useState } from 'react';
import { Download, Eye, EyeOff, FileText, Loader2, Trash2, Upload } from 'lucide-react';
import {
    deleteImportOrderFile,
    getImportOrderFileUrl,
    updateImportOrderFile,
    uploadImportOrderFiles,
} from '../../../api/importOrders';
import { Field, Section, inputClass } from '../FormField';
import {
    DOCUMENT_CATEGORIES,
    DOCUMENT_CATEGORY_LABELS,
    MAX_DOCUMENTS_PER_UPLOAD,
    MAX_DOCUMENT_BYTES,
    formatBytes,
    formatDate,
} from '../../../constants/importOrders';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { DocumentCategory, ImportOrderDetail } from '../../../types/importOrders';

const ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp';

/** Dokumenty: invoices etc. Visibility decides whether the client can download it. */
export default function DocumentsTab({ order, onReload }: { order: ImportOrderDetail; onReload: () => void }) {
    const documents = order.files.filter(f => f.kind === 'DOCUMENT');
    const input = useRef<HTMLInputElement>(null);
    const [files, setFiles] = useState<File[]>([]);
    const [category, setCategory] = useState<DocumentCategory>('INVOICE_DEALER');
    const [visible, setVisible] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [confirmId, setConfirmId] = useState<string | null>(null);

    if (!order.storageEnabled) {
        return <Section title="Dokumenty"><p className="text-sm text-amber-300">Przechowywanie plików nie jest skonfigurowane na serwerze — dodawanie dokumentów jest chwilowo niedostępne.</p></Section>;
    }

    const upload = async () => {
        const tooBig = files.find(f => f.size > MAX_DOCUMENT_BYTES);
        if (tooBig) {
            setError(`${tooBig.name}: plik większy niż 20 MB.`);
            return;
        }
        if (files.length > MAX_DOCUMENTS_PER_UPLOAD) {
            setError(`Najwyżej ${MAX_DOCUMENTS_PER_UPLOAD} plików naraz.`);
            return;
        }
        setBusy(true);
        setError(null);
        try {
            await uploadImportOrderFiles(order.id, files, 'DOCUMENT', category, visible);
            setFiles([]);
            if (input.current) input.current.value = '';
            onReload();
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się wysłać plików (dozwolone: PDF, JPEG, PNG, WEBP).'));
        } finally {
            setBusy(false);
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

    const download = async (fileId: string) => {
        try {
            const { url } = await getImportOrderFileUrl(order.id, fileId);
            // Served with Content-Disposition: attachment, so this downloads without leaving the page
            // (a window.open after an await would be caught by popup blockers)
            window.location.assign(url);
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się pobrać pliku.'));
        }
    };

    return (
        <div className="space-y-4">
            <Section title="Dodaj dokumenty">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Field label={`Pliki (PDF, JPEG, PNG, WEBP; do 20 MB, najwyżej ${MAX_DOCUMENTS_PER_UPLOAD})`}>
                        <input ref={input} type="file" multiple accept={ACCEPT} onChange={e => setFiles(Array.from(e.target.files ?? []))}
                               className="block w-full text-sm text-avtovo-text-secondary file:mr-3 file:rounded-lg file:border-0 file:bg-avtovo-border file:px-3 file:py-2 file:text-avtovo-text" />
                    </Field>
                    <Field label="Kategoria">
                        <select value={category} onChange={e => setCategory(e.target.value as DocumentCategory)} className={inputClass}>
                            {DOCUMENT_CATEGORIES.map(c => <option key={c} value={c}>{DOCUMENT_CATEGORY_LABELS[c]}</option>)}
                        </select>
                    </Field>
                </div>
                <label className="flex items-center gap-2 text-sm text-avtovo-text">
                    <input type="checkbox" checked={visible} onChange={e => setVisible(e.target.checked)} />
                    Widoczne dla klienta
                </label>
                <button onClick={upload} disabled={busy || files.length === 0}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-50">
                    {busy ? <Loader2 size={15} className="animate-spin" /> : <Upload size={15} />} Wyślij {files.length > 0 ? `(${files.length})` : ''}
                </button>
            </Section>

            {error && <p className="text-sm text-red-400">{error}</p>}

            <Section title={`Dokumenty (${documents.length})`}>
                {documents.length === 0 ? <p className="text-sm text-avtovo-text-secondary">Brak dokumentów.</p> : (
                    <ul className="space-y-2">
                        {documents.map(d => (
                            <li key={d.id} className="flex flex-wrap items-center gap-3 bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2.5">
                                <FileText size={18} className="text-avtovo-accent shrink-0" />
                                <div className="min-w-0 flex-1">
                                    <p className="text-sm text-avtovo-text truncate">{d.originalName}</p>
                                    <p className="text-xs text-avtovo-text-secondary">
                                        {d.uploadedByClient && <span className="mr-1.5 rounded bg-avtovo-accent/15 px-1.5 py-0.5 text-avtovo-accent">Od klienta</span>}
                                        {formatBytes(d.sizeBytes)} · {formatDate(d.createdAt)}
                                    </p>
                                </div>
                                <select value={d.category} disabled={busy}
                                        onChange={e => run(() => updateImportOrderFile(order.id, d.id, { category: e.target.value }), 'Nie udało się zmienić kategorii.')}
                                        className="bg-avtovo-card border border-avtovo-border rounded-lg px-2 py-1.5 text-xs text-avtovo-text">
                                    {DOCUMENT_CATEGORIES.map(c => <option key={c} value={c}>{DOCUMENT_CATEGORY_LABELS[c]}</option>)}
                                </select>
                                <button disabled={busy}
                                        onClick={() => run(() => updateImportOrderFile(order.id, d.id, { clientVisible: !d.clientVisible }), 'Nie udało się zmienić widoczności.')}
                                        className={`inline-flex items-center gap-1 text-xs px-2 py-1.5 rounded-lg border ${d.clientVisible
                                            ? 'border-emerald-500/40 text-emerald-400' : 'border-avtovo-border text-avtovo-text-secondary'}`}
                                        title={d.clientVisible ? 'Klient widzi ten dokument' : 'Ukryty przed klientem'}>
                                    {d.clientVisible ? <><Eye size={13} /> Widoczny</> : <><EyeOff size={13} /> Ukryty</>}
                                </button>
                                <button onClick={() => download(d.id)} aria-label="Pobierz" className="text-avtovo-text-secondary hover:text-avtovo-accent"><Download size={16} /></button>
                                {confirmId === d.id ? (
                                    <span className="flex items-center gap-2">
                                        <button onClick={() => run(() => deleteImportOrderFile(order.id, d.id), 'Nie udało się usunąć pliku.')} className="text-xs text-red-400">Usuń</button>
                                        <button onClick={() => setConfirmId(null)} className="text-xs text-avtovo-text-secondary">Anuluj</button>
                                    </span>
                                ) : (
                                    <button onClick={() => setConfirmId(d.id)} aria-label="Usuń" className="text-avtovo-text-secondary hover:text-red-400"><Trash2 size={16} /></button>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </Section>
        </div>
    );
}
