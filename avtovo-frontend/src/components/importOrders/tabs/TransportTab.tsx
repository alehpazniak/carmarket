import { useState, type FormEvent } from 'react';
import { Loader2 } from 'lucide-react';
import { updateImportOrder } from '../../../api/importOrders';
import { Field, Section, inputClass } from '../FormField';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { ImportOrderDetail, ImportOrderFields } from '../../../types/importOrders';

const FIELDS: { key: keyof ImportOrderFields; label: string; type?: 'date'; placeholder?: string }[] = [
    { key: 'paymentDeadline', label: 'Termin płatności na aukcji', type: 'date' },
    { key: 'titleReceivedAt', label: 'Title otrzymany', type: 'date' },
    { key: 'portOfLoading', label: 'Port załadunku', placeholder: 'np. Savannah, GA' },
    { key: 'shippingLine', label: 'Armator', placeholder: 'np. MSC' },
    { key: 'containerNumber', label: 'Numer kontenera', placeholder: 'np. MSCU1234566' },
    { key: 'bookingNumber', label: 'Numer bookingu' },
    { key: 'billOfLading', label: 'Konosament (B/L)' },
    { key: 'vesselName', label: 'Statek' },
    { key: 'etd', label: 'Wypłynięcie (ETD)', type: 'date' },
    { key: 'eta', label: 'Przypłynięcie (ETA)', type: 'date' },
    { key: 'destinationPort', label: 'Port docelowy', placeholder: 'np. Bremerhaven' },
    { key: 'customsClearedAt', label: 'Odprawa celna', type: 'date' },
];

function toForm(o: ImportOrderDetail): Record<string, string> {
    return Object.fromEntries(FIELDS.map(f => [f.key, (o[f.key as keyof ImportOrderDetail] as string | null) ?? '']));
}

/** Transport: shipping details; empty fields are hidden from the client. */
export default function TransportTab({ order, onChange }: { order: ImportOrderDetail; onChange: (o: ImportOrderDetail) => void }) {
    const [form, setForm] = useState(() => toForm(order));
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

    const save = async (e: FormEvent) => {
        e.preventDefault();
        const fields = Object.fromEntries(FIELDS.map(f => [f.key, form[f.key].trim() || null])) as ImportOrderFields;
        setSaving(true);
        setMessage(null);
        try {
            const updated = await updateImportOrder(order.id, fields);
            onChange(updated);
            setForm(toForm(updated));
            setMessage({ ok: true, text: 'Zapisano.' });
        } catch (err) {
            const text = apiErrorMessage(err, 'Nie udało się zapisać.');
            setMessage({ ok: false, text: text.includes('containerNumber') ? 'Nieprawidłowy numer kontenera (ISO 6346, np. MSCU1234566).' : text });
        } finally {
            setSaving(false);
        }
    };

    return (
        <form onSubmit={save} className="space-y-4">
            <Section title="Szczegóły transportu">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {FIELDS.map(f => (
                        <Field key={f.key} label={f.label}>
                            <input
                                type={f.type ?? 'text'}
                                value={form[f.key]}
                                placeholder={f.placeholder}
                                onChange={e => setForm(prev => ({ ...prev, [f.key]: e.target.value }))}
                                className={`${inputClass} ${f.key === 'containerNumber' ? 'font-mono uppercase' : ''}`}
                            />
                        </Field>
                    ))}
                </div>
                <p className="text-xs text-avtovo-muted">Klient widzi tylko wypełnione pola. Numer kontenera jest sprawdzany (cyfra kontrolna ISO 6346).</p>
            </Section>
            <div className="flex items-center gap-3">
                <button type="submit" disabled={saving}
                        className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                    {saving && <Loader2 size={15} className="animate-spin" />} Zapisz
                </button>
                {message && <span className={`text-sm ${message.ok ? 'text-emerald-400' : 'text-red-400'}`}>{message.text}</span>}
            </div>
        </form>
    );
}
