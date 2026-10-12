import { useCallback, useEffect, useState } from 'react';
import { Eye, KeyRound, Loader2, LogIn, ShieldOff, ShieldCheck, XCircle } from 'lucide-react';
import { getAccessLog, getClientPreview, resetClientAccess, setClientAccessEnabled } from '../../../api/importOrders';
import { Section } from '../FormField';
import CopyButton from '../CopyButton';
import CredentialsModal from '../CredentialsModal';
import ClientOrderViewCard from '../ClientOrderViewCard';
import { carTitle, formatDateTime } from '../../../constants/importOrders';
import { apiErrorMessage } from '../../../utils/importOrderUi';
import type { AccessLogEntry, ClientCredentials, ClientOrderView, ImportOrderDetail } from '../../../types/importOrders';

const EVENT_TEXT: Record<AccessLogEntry['event'], { text: string; icon: typeof Eye; className: string }> = {
    VIEW: { text: 'Klient otworzył stronę', icon: Eye, className: 'text-avtovo-text' },
    LOGIN_OK: { text: 'Klient zalogował się hasłem', icon: LogIn, className: 'text-emerald-400' },
    LOGIN_FAILED: { text: 'Błędne hasło', icon: XCircle, className: 'text-red-400' },
};

/** Dostęp klienta: link, password reset, on/off switch, access log and "preview as client". */
export default function ClientAccessTab({ order, onChange }: { order: ImportOrderDetail; onChange: (o: ImportOrderDetail) => void }) {
    const [log, setLog] = useState<AccessLogEntry[] | null>(null);
    const [preview, setPreview] = useState<ClientOrderView | null>(null);
    const [credentials, setCredentials] = useState<ClientCredentials | null>(null);
    const [rotateLink, setRotateLink] = useState(false);
    const [confirmReset, setConfirmReset] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadLog = useCallback(() => {
        getAccessLog(order.id).then(setLog).catch(err => setError(apiErrorMessage(err, 'Nie udało się wczytać historii dostępu.')));
    }, [order.id]);

    useEffect(() => {
        loadLog();
    }, [loadLog]);

    const reset = async () => {
        setBusy(true);
        setError(null);
        try {
            setCredentials(await resetClientAccess(order.id, rotateLink));
            setConfirmReset(false);
            setRotateLink(false);
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się zresetować hasła.'));
        } finally {
            setBusy(false);
        }
    };

    const toggleAccess = async () => {
        setBusy(true);
        setError(null);
        try {
            onChange(await setClientAccessEnabled(order.id, !order.access.enabled));
        } catch (err) {
            setError(apiErrorMessage(err));
        } finally {
            setBusy(false);
        }
    };

    const showPreview = async () => {
        setBusy(true);
        setError(null);
        try {
            setPreview(await getClientPreview(order.id));
        } catch (err) {
            setError(apiErrorMessage(err, 'Nie udało się wczytać podglądu.'));
        } finally {
            setBusy(false);
        }
    };

    const locked = order.access.lockedUntil && new Date(order.access.lockedUntil) > new Date();

    return (
        <div className="space-y-4">
            <Section title="Link dla klienta">
                <div className="flex items-center gap-2">
                    <code className="flex-1 min-w-0 truncate bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text">{order.access.clientLink}</code>
                    <CopyButton text={order.access.clientLink} />
                </div>
                <p className="text-xs text-avtovo-muted">Klient potrzebuje też hasła. Hasła nie przechowujemy — jeśli klient je zgubił, zresetuj je poniżej.</p>
                {locked && (
                    <p className="text-sm text-amber-300">Dostęp jest chwilowo zablokowany po zbyt wielu błędnych hasłach (do {formatDateTime(order.access.lockedUntil)}). Reset hasła odblokowuje go od razu.</p>
                )}
            </Section>

            <Section title="Hasło i dostęp">
                {confirmReset ? (
                    <div className="space-y-3">
                        <p className="text-sm text-avtovo-text">Nowe hasło zastąpi stare, a otwarta u klienta strona poprosi o nie ponownie.</p>
                        <label className="flex items-center gap-2 text-sm text-avtovo-text-secondary">
                            <input type="checkbox" checked={rotateLink} onChange={e => setRotateLink(e.target.checked)} />
                            Wygeneruj też nowy link (stary przestanie działać)
                        </label>
                        <div className="flex gap-2">
                            <button onClick={reset} disabled={busy}
                                    className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2 rounded-lg text-sm disabled:opacity-60">
                                {busy && <Loader2 size={15} className="animate-spin" />} Zresetuj hasło
                            </button>
                            <button onClick={() => setConfirmReset(false)} className="px-3 py-2 text-sm text-avtovo-text-secondary">Anuluj</button>
                        </div>
                    </div>
                ) : (
                    <div className="flex flex-wrap gap-2">
                        <button onClick={() => setConfirmReset(true)}
                                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                            <KeyRound size={15} /> Zresetuj hasło
                        </button>
                        <button onClick={toggleAccess} disabled={busy}
                                className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg border text-sm disabled:opacity-60 ${order.access.enabled
                                    ? 'border-avtovo-border text-red-400 hover:border-red-500/50' : 'border-emerald-500/40 text-emerald-400'}`}>
                            {order.access.enabled ? <><ShieldOff size={15} /> Wyłącz dostęp klienta</> : <><ShieldCheck size={15} /> Włącz dostęp klienta</>}
                        </button>
                        <button onClick={showPreview} disabled={busy}
                                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent">
                            <Eye size={15} /> Podgląd jako klient
                        </button>
                    </div>
                )}
                {!order.access.enabled && <p className="text-sm text-amber-300">Dostęp wyłączony — link i hasło nie działają.</p>}
            </Section>

            {error && <p className="text-sm text-red-400">{error}</p>}

            {preview && (
                <Section title="Podgląd: tak widzi to klient" actions={
                    <button onClick={() => setPreview(null)} className="text-sm text-avtovo-text-secondary hover:text-avtovo-text">Zamknij</button>
                }>
                    <div className="max-w-xl mx-auto"><ClientOrderViewCard view={preview} /></div>
                </Section>
            )}

            <Section title="Historia dostępu" actions={
                <button onClick={loadLog} className="text-sm text-avtovo-text-secondary hover:text-avtovo-text">Odśwież</button>
            }>
                {!log ? <Loader2 size={16} className="animate-spin text-avtovo-muted" /> : log.length === 0 ? (
                    <p className="text-sm text-avtovo-text-secondary">Klient jeszcze nie otwierał strony.</p>
                ) : (
                    <ul className="space-y-2">
                        {log.map((entry, i) => {
                            const meta = EVENT_TEXT[entry.event];
                            const Icon = meta.icon;
                            return (
                                <li key={i} className="flex items-start gap-2 text-sm">
                                    <Icon size={15} className={`mt-0.5 shrink-0 ${meta.className}`} />
                                    <div className="min-w-0">
                                        <p className={meta.className}>{meta.text}: {formatDateTime(entry.at)}</p>
                                        <p className="text-xs text-avtovo-muted truncate">{[entry.ipPrefix, entry.userAgent].filter(Boolean).join(' · ')}</p>
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </Section>

            {credentials && (
                <CredentialsModal credentials={credentials} carTitle={carTitle(order)} title="Nowe hasło dla klienta"
                                  onClose={() => {
                                      setCredentials(null);
                                      onChange({ ...order, access: { ...order.access, clientLink: credentials.clientLink, lockedUntil: null, failedAttempts: 0 } });
                                  }} />
            )}
        </div>
    );
}
