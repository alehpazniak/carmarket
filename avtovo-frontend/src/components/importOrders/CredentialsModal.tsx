import { KeyRound, ShieldAlert, X } from 'lucide-react';
import CopyButton from './CopyButton';
import type { ClientCredentials } from '../../types/importOrders';

interface Props {
    credentials: ClientCredentials;
    carTitle: string;
    title?: string;
    onClose: () => void;
}

/**
 * The client link and password, shown exactly once: only the password hash is stored, so after
 * closing this the only way to give the client access again is a reset.
 */
export default function CredentialsModal({ credentials, carTitle, title = 'Dostęp dla klienta', onClose }: Props) {
    const linkMessage = `Dzień dobry, pod tym linkiem może Pan/Pani śledzić import samochodu ${carTitle}: ${credentials.clientLink}\nHasło wyślę osobną wiadomością.`;
    const passwordMessage = `Hasło do strony zlecenia ${carTitle}: ${credentials.password}`;
    const combined = `Dzień dobry, pod tym linkiem może Pan/Pani śledzić import samochodu ${carTitle}:\n${credentials.clientLink}\nHasło: ${credentials.password}`;

    return (
        <div className="fixed inset-0 z-[60] bg-black/70 flex items-end sm:items-center justify-center p-0 sm:p-4" role="dialog" aria-modal="true">
            <div className="w-full sm:max-w-lg bg-avtovo-card border border-avtovo-border rounded-t-2xl sm:rounded-2xl p-5 sm:p-6 space-y-4 max-h-[92vh] overflow-y-auto">
                <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                        <KeyRound size={20} className="text-avtovo-accent" />
                        <h2 className="text-lg font-semibold text-avtovo-text">{title}</h2>
                    </div>
                    <button onClick={onClose} aria-label="Zamknij" className="text-avtovo-text-secondary hover:text-avtovo-text">
                        <X size={20} />
                    </button>
                </div>

                <div className="flex gap-2 rounded-lg bg-amber-500/10 border border-amber-500/30 p-3 text-sm text-amber-300">
                    <ShieldAlert size={18} className="shrink-0 mt-0.5" />
                    <p>Hasło widzisz tylko teraz — nie przechowujemy go. Po zamknięciu tego okna można je jedynie zresetować.</p>
                </div>

                <div className="space-y-1.5">
                    <p className="text-xs uppercase tracking-wide text-avtovo-text-secondary">Link dla klienta</p>
                    <div className="flex items-center gap-2">
                        <code className="flex-1 min-w-0 truncate bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text">
                            {credentials.clientLink}
                        </code>
                        <CopyButton text={credentials.clientLink} />
                    </div>
                </div>

                <div className="space-y-1.5">
                    <p className="text-xs uppercase tracking-wide text-avtovo-text-secondary">Hasło</p>
                    <div className="flex items-center gap-2">
                        <code className="flex-1 bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-lg tracking-wider font-semibold text-avtovo-text">
                            {credentials.password}
                        </code>
                        <CopyButton text={credentials.password} />
                    </div>
                </div>

                <div className="space-y-2">
                    <p className="text-sm text-avtovo-text-secondary">
                        Najbezpieczniej wyślij link i hasło <strong className="text-avtovo-text">różnymi kanałami</strong>, np. link e-mailem, a hasło SMS-em.
                    </p>
                    <div className="flex flex-wrap gap-2">
                        <CopyButton text={linkMessage} label="Wiadomość z linkiem" />
                        <CopyButton text={passwordMessage} label="Wiadomość z hasłem" />
                        <CopyButton text={combined} label="Wszystko w jednej wiadomości" />
                    </div>
                    <pre className="whitespace-pre-wrap text-xs bg-avtovo-bg border border-avtovo-border rounded-lg p-3 text-avtovo-text-secondary">{combined}</pre>
                </div>

                <button
                    onClick={onClose}
                    className="w-full bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg py-2.5 text-sm font-medium transition-colors"
                >
                    Zapisałem dane, zamknij
                </button>
            </div>
        </div>
    );
}
