import { useState, type ReactNode } from 'react';
import { AlertTriangle, Loader2, X } from 'lucide-react';
import { errorMessage } from '../hooks';

export const MIN_REASON_LENGTH = 5;

interface Props {
    title: string;
    message?: string;
    confirmLabel: string;
    /** Red confirm button. */
    destructive?: boolean;
    /** Ask for a reason (at least 5 characters); every destructive admin action needs one. */
    requireReason?: boolean;
    reasonLabel?: string;
    /** Extra inputs shown above the reason (their state lives in the caller). */
    children?: ReactNode;
    /** Disables the confirm button, e.g. while an extra input is invalid. */
    confirmDisabled?: boolean;
    onConfirm: (reason: string) => Promise<void>;
    onClose: () => void;
}

/**
 * Rendered only while open. Keeps itself open and shows the server's message if onConfirm fails,
 * so the admin doesn't lose the typed reason.
 */
export default function ConfirmDialog({ title, message, confirmLabel, destructive, requireReason = true,
                                          reasonLabel = 'Powód (widoczny w dzienniku zdarzeń)', children, confirmDisabled,
                                          onConfirm, onClose }: Props) {
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const tooShort = requireReason && reason.trim().length < MIN_REASON_LENGTH;

    const submit = async () => {
        if (tooShort || busy || confirmDisabled) return;
        setBusy(true);
        setError(null);
        try {
            await onConfirm(reason.trim());
            onClose();
        } catch (e) {
            setError(errorMessage(e, 'Nie udało się wykonać operacji.'));
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true"
             onClick={busy ? undefined : onClose}>
            <div className="w-full max-w-md bg-avtovo-card border border-avtovo-border rounded-xl p-5 space-y-4" onClick={e => e.stopPropagation()}>
                <div className="flex items-start justify-between gap-3">
                    <h2 className="flex items-center gap-2 text-lg font-semibold text-avtovo-text">
                        {destructive && <AlertTriangle size={18} className="text-red-400" />} {title}
                    </h2>
                    <button onClick={onClose} disabled={busy} aria-label="Zamknij" className="text-avtovo-text-secondary hover:text-avtovo-text">
                        <X size={18} />
                    </button>
                </div>
                {message && <p className="text-sm text-avtovo-text-secondary">{message}</p>}
                {children}
                {requireReason && (
                    <label className="block space-y-1.5">
                        <span className="text-sm text-avtovo-text">{reasonLabel}</span>
                        <textarea
                            value={reason}
                            onChange={e => setReason(e.target.value)}
                            rows={3}
                            maxLength={500}
                            autoFocus
                            className="w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text focus:outline-none focus:border-avtovo-accent"
                        />
                        {reason.length > 0 && tooShort && (
                            <span className="text-xs text-avtovo-text-secondary">Co najmniej {MIN_REASON_LENGTH} znaków.</span>
                        )}
                    </label>
                )}
                {error && <p className="text-sm text-red-400">{error}</p>}
                <div className="flex justify-end gap-2">
                    <button onClick={onClose} disabled={busy}
                            className="px-4 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text">
                        Anuluj
                    </button>
                    <button onClick={submit} disabled={tooShort || busy || confirmDisabled}
                            className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white disabled:opacity-50 ${destructive
                                ? 'bg-red-600 hover:bg-red-700' : 'bg-avtovo-accent hover:bg-avtovo-accent-hover'}`}>
                        {busy && <Loader2 size={14} className="animate-spin" />} {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
