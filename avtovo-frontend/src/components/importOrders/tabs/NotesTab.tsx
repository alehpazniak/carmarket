import { useState } from 'react';
import { Loader2, Lock } from 'lucide-react';
import { updateImportOrder } from '../../../api/importOrders';
import { Section, inputClass } from '../FormField';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { ImportOrderDetail } from '../../../types/importOrders';

/** Notatki wewnętrzne: never shown to the client. */
export default function NotesTab({ order, onChange }: { order: ImportOrderDetail; onChange: (o: ImportOrderDetail) => void }) {
    const [notes, setNotes] = useState(order.internalNotes ?? '');
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

    const save = async () => {
        setSaving(true);
        setMessage(null);
        try {
            const updated = await updateImportOrder(order.id, { internalNotes: notes.trim() || null });
            onChange(updated);
            setMessage({ ok: true, text: 'Zapisano.' });
        } catch (err) {
            setMessage({ ok: false, text: apiErrorMessage(err, 'Nie udało się zapisać.') });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Section title="Notatki wewnętrzne">
            <p className="flex items-center gap-1.5 text-xs text-avtovo-muted"><Lock size={12} /> Widoczne tylko dla Ciebie — nigdy dla klienta.</p>
            <textarea rows={10} value={notes} onChange={e => setNotes(e.target.value)} maxLength={20000} className={inputClass}
                      placeholder="Marża, ustalenia z klientem, uwagi do transportu…" />
            <div className="flex items-center gap-3">
                <button onClick={save} disabled={saving}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                    {saving && <Loader2 size={15} className="animate-spin" />} Zapisz
                </button>
                {message && <span className={`text-sm ${message.ok ? 'text-emerald-400' : 'text-red-400'}`}>{message.text}</span>}
            </div>
        </Section>
    );
}
