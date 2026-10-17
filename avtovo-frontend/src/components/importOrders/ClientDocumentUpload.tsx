import { useRef, useState } from 'react';
import { Loader2, Upload } from 'lucide-react';
import {
    CLIENT_DOCUMENT_CATEGORIES,
    DOCUMENT_CATEGORY_LABELS,
    MAX_CLIENT_DOCUMENTS_PER_UPLOAD,
    MAX_DOCUMENT_BYTES,
} from '../../constants/importOrders';
import type { DocumentCategory } from '../../types/importOrders';

const ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp';

/**
 * The client's own documents on /zlecenie/:code, e.g. a payment confirmation for the dealer's invoice.
 * {@code onUpload} rejects with a ready-to-show Polish message.
 */
export default function ClientDocumentUpload({ onUpload }: { onUpload: (files: File[], category: DocumentCategory) => Promise<void> }) {
    const input = useRef<HTMLInputElement>(null);
    const [files, setFiles] = useState<File[]>([]);
    const [category, setCategory] = useState<DocumentCategory>('PAYMENT_CONFIRMATION');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

    const send = async () => {
        const tooBig = files.find(f => f.size > MAX_DOCUMENT_BYTES);
        if (tooBig) {
            setMessage({ ok: false, text: `${tooBig.name}: plik większy niż 20 MB.` });
            return;
        }
        if (files.length > MAX_CLIENT_DOCUMENTS_PER_UPLOAD) {
            setMessage({ ok: false, text: `Najwyżej ${MAX_CLIENT_DOCUMENTS_PER_UPLOAD} plików naraz.` });
            return;
        }
        setBusy(true);
        setMessage(null);
        try {
            await onUpload(files, category);
            setFiles([]);
            if (input.current) input.current.value = '';
            setMessage({ ok: true, text: 'Wysłano — dealer zobaczy plik w zleceniu.' });
        } catch (err) {
            setMessage({ ok: false, text: err instanceof Error ? err.message : 'Nie udało się wysłać plików.' });
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="space-y-3 border-t border-avtovo-border pt-4">
            <h3 className="text-sm font-medium text-avtovo-text">Wyślij dokument do dealera</h3>
            <p className="text-xs text-avtovo-text-secondary">
                Np. potwierdzenie przelewu za fakturę. PDF, JPEG, PNG lub WEBP, do 20 MB, najwyżej {MAX_CLIENT_DOCUMENTS_PER_UPLOAD} plików naraz.
            </p>
            <input ref={input} type="file" multiple accept={ACCEPT} onChange={e => { setFiles(Array.from(e.target.files ?? [])); setMessage(null); }}
                   className="block w-full text-sm text-avtovo-text-secondary file:mr-3 file:rounded-lg file:border-0 file:bg-avtovo-border file:px-3 file:py-2 file:text-avtovo-text" />
            <div className="flex flex-col sm:flex-row gap-2">
                <select value={category} onChange={e => setCategory(e.target.value as DocumentCategory)}
                        className="flex-1 bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2.5 text-sm text-avtovo-text focus:outline-none focus:border-avtovo-accent">
                    {CLIENT_DOCUMENT_CATEGORIES.map(c => <option key={c} value={c}>{DOCUMENT_CATEGORY_LABELS[c]}</option>)}
                </select>
                <button onClick={send} disabled={busy || files.length === 0}
                        className="inline-flex items-center justify-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2.5 rounded-lg text-sm font-medium disabled:opacity-50">
                    {busy ? <Loader2 size={15} className="animate-spin" /> : <Upload size={15} />} Wyślij {files.length > 0 ? `(${files.length})` : ''}
                </button>
            </div>
            {message && <p className={`text-sm ${message.ok ? 'text-emerald-400' : 'text-red-400'}`}>{message.text}</p>}
        </div>
    );
}
