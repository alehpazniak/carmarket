import { useState } from 'react';
import axios from 'axios';
import { Flag, Loader2, X } from 'lucide-react';
import { reportCar } from '../api/cars';
import { REPORT_REASON_LABELS, type ReportReason } from '../constants/reports';

interface Props {
    carId: string;
    onClose: () => void;
}

/** "Zgłoś ogłoszenie" from CarDetail: reason + optional comment; a moderator reviews it. */
export default function ReportListingDialog({ carId, onClose }: Props) {
    const [reason, setReason] = useState<ReportReason | ''>('');
    const [comment, setComment] = useState('');
    const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle');
    const [error, setError] = useState<string | null>(null);

    const submit = async () => {
        if (!reason) return;
        setState('sending');
        setError(null);
        try {
            await reportCar(carId, reason, comment.trim());
            setState('sent');
        } catch (e) {
            setState('idle');
            const status = axios.isAxiosError(e) ? e.response?.status : undefined;
            setError(status === 409 ? 'To ogłoszenie zostało już przez Ciebie zgłoszone.'
                : status === 429 ? 'Osiągnięto dzienny limit zgłoszeń. Spróbuj jutro.'
                    : status === 403 ? 'Nie możesz zgłosić własnego ogłoszenia.'
                        : 'Nie udało się wysłać zgłoszenia. Spróbuj ponownie.');
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true" onClick={onClose}>
            <div className="w-full max-w-md bg-avtovo-card border border-avtovo-border rounded-xl p-5 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex items-start justify-between gap-3">
                    <h2 className="flex items-center gap-2 text-lg font-semibold text-avtovo-text"><Flag size={18} /> Zgłoś ogłoszenie</h2>
                    <button onClick={onClose} aria-label="Zamknij" className="text-avtovo-text-secondary hover:text-avtovo-text"><X size={18} /></button>
                </div>
                {state === 'sent' ? (
                    <>
                        <p className="text-sm text-avtovo-text">Dziękujemy. Moderator sprawdzi to ogłoszenie.</p>
                        <div className="flex justify-end">
                            <button onClick={onClose} className="px-4 py-2 rounded-lg text-sm bg-avtovo-accent hover:bg-avtovo-accent-hover text-white">Zamknij</button>
                        </div>
                    </>
                ) : (
                    <>
                        <fieldset className="space-y-2">
                            <legend className="text-sm text-avtovo-text mb-2">Co jest nie tak?</legend>
                            {(Object.keys(REPORT_REASON_LABELS) as ReportReason[]).map(r => (
                                <label key={r} className="flex items-center gap-2 text-sm text-avtovo-text-secondary cursor-pointer">
                                    <input type="radio" name="reason" value={r} checked={reason === r} onChange={() => setReason(r)} />
                                    {REPORT_REASON_LABELS[r]}
                                </label>
                            ))}
                        </fieldset>
                        <label className="block space-y-1.5">
                            <span className="text-sm text-avtovo-text">Komentarz (opcjonalnie)</span>
                            <textarea value={comment} onChange={e => setComment(e.target.value)} rows={3} maxLength={1000}
                                      className="w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text focus:outline-none focus:border-avtovo-accent" />
                        </label>
                        {error && <p className="text-sm text-red-400">{error}</p>}
                        <div className="flex justify-end gap-2">
                            <button onClick={onClose} className="px-4 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text">Anuluj</button>
                            <button onClick={submit} disabled={!reason || state === 'sending'}
                                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 disabled:opacity-50">
                                {state === 'sending' && <Loader2 size={14} className="animate-spin" />} Wyślij zgłoszenie
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
