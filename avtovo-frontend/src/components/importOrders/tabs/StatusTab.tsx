import { useState, type FormEvent } from 'react';
import { ArrowRight, Loader2, Lock, Trash2 } from 'lucide-react';
import { addImportOrderEvent, deleteImportOrderEvent } from '../../../api/importOrders';
import StatusBadge from '../StatusBadge';
import { Field, Section, inputClass } from '../FormField';
import { ALL_STATUSES, STATUS_LABELS, formatDate, nextStatus } from '../../../constants/importOrders';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { ImportOrderDetail, ImportOrderStatus } from '../../../types/importOrders';

/** Local yyyy-MM-dd of today. */
function today(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** A picked day as an instant at local noon, so the date shows the same in every time zone nearby. */
function dayToInstant(day: string): string {
    return new Date(`${day}T12:00:00`).toISOString();
}

/** Status: the timeline (newest first) and adding / removing steps. Any status may be set, for corrections. */
export default function StatusTab({ order, onChange }: { order: ImportOrderDetail; onChange: (o: ImportOrderDetail) => void }) {
    const next = nextStatus(order.status);
    const [status, setStatus] = useState<ImportOrderStatus>(next ?? 'WON');
    const [day, setDay] = useState(today());
    const [clientNote, setClientNote] = useState('');
    const [internalNote, setInternalNote] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [confirmId, setConfirmId] = useState<string | null>(null);

    const add = async (event: { status: ImportOrderStatus; occurredAt: string; clientNote?: string; internalNote?: string }) => {
        setSaving(true);
        setError(null);
        try {
            const updated = await addImportOrderEvent(order.id, event);
            onChange(updated);
            setClientNote('');
            setInternalNote('');
            setStatus(nextStatus(updated.status) ?? 'WON');
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się dodać statusu.'));
        } finally {
            setSaving(false);
        }
    };

    const submit = (e: FormEvent) => {
        e.preventDefault();
        add({
            status,
            occurredAt: dayToInstant(day),
            clientNote: clientNote.trim() || undefined,
            internalNote: internalNote.trim() || undefined,
        });
    };

    const remove = async (eventId: string) => {
        setSaving(true);
        setError(null);
        try {
            onChange(await deleteImportOrderEvent(order.id, eventId));
            setConfirmId(null);
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się usunąć statusu.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="space-y-4">
            <Section title="Aktualny status" actions={next && (
                <button onClick={() => add({ status: next, occurredAt: new Date().toISOString() })} disabled={saving}
                        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-avtovo-accent hover:bg-avtovo-accent-hover text-white text-sm disabled:opacity-60">
                    <ArrowRight size={15} /> {STATUS_LABELS[next]}
                </button>
            )}>
                <StatusBadge status={order.status} className="text-sm" />
                <p className="text-xs text-avtovo-muted">Aktualny jest status z najpóźniejszą datą. Przycisk obok dodaje kolejny krok z dzisiejszą datą.</p>
            </Section>

            <Section title="Dodaj status">
                <form onSubmit={submit} className="space-y-3">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Field label="Status">
                            <select value={status} onChange={e => setStatus(e.target.value as ImportOrderStatus)} className={inputClass}>
                                {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
                            </select>
                        </Field>
                        <Field label="Data">
                            <input type="date" value={day} max={today()} onChange={e => setDay(e.target.value)} required className={inputClass} />
                        </Field>
                    </div>
                    <Field label="Notatka dla klienta (widoczna na stronie zlecenia)">
                        <textarea rows={2} value={clientNote} onChange={e => setClientNote(e.target.value)} maxLength={2000} className={inputClass} />
                    </Field>
                    <Field label="Notatka wewnętrzna (tylko dla Ciebie)">
                        <textarea rows={2} value={internalNote} onChange={e => setInternalNote(e.target.value)} maxLength={5000} className={inputClass} />
                    </Field>
                    <button type="submit" disabled={saving}
                            className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                        {saving && <Loader2 size={15} className="animate-spin" />} Dodaj
                    </button>
                </form>
            </Section>

            {error && <p className="text-sm text-red-400">{error}</p>}

            <Section title="Historia">
                {order.events.length === 0 ? (
                    <p className="text-sm text-avtovo-text-secondary">Brak statusów.</p>
                ) : (
                    <ol className="space-y-3">
                        {order.events.map((e, i) => (
                            <li key={e.id} className={`rounded-lg border p-3 ${i === 0 ? 'border-avtovo-accent/50 bg-avtovo-accent/5' : 'border-avtovo-border'}`}>
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                    <div className="flex items-center gap-2">
                                        <StatusBadge status={e.status} />
                                        <span className="text-xs text-avtovo-text-secondary">{formatDate(e.occurredAt)}</span>
                                    </div>
                                    {confirmId === e.id ? (
                                        <span className="flex items-center gap-2">
                                            <button onClick={() => remove(e.id)} disabled={saving} className="text-xs text-red-400 hover:underline">Usuń</button>
                                            <button onClick={() => setConfirmId(null)} className="text-xs text-avtovo-text-secondary">Anuluj</button>
                                        </span>
                                    ) : (
                                        <button onClick={() => setConfirmId(e.id)} aria-label="Usuń status" className="text-avtovo-text-secondary hover:text-red-400">
                                            <Trash2 size={15} />
                                        </button>
                                    )}
                                </div>
                                {e.clientNote && <p className="mt-2 text-sm text-avtovo-text whitespace-pre-line">{e.clientNote}</p>}
                                {e.internalNote && (
                                    <p className="mt-1 flex gap-1.5 text-sm text-avtovo-text-secondary whitespace-pre-line">
                                        <Lock size={12} className="mt-1 shrink-0" /> {e.internalNote}
                                    </p>
                                )}
                            </li>
                        ))}
                    </ol>
                )}
            </Section>
        </div>
    );
}
