import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Eye, LogOut, ShieldCheck, ShieldOff, UserCog } from 'lucide-react';
import {
    blockUser, changeUserRole, getAdminUser, getAdminUserProfile, getAuditLog, listAdminListings, listAdminPayments,
    logoutUserEverywhere, unblockUser,
    type AdminUserProfile, type Role,
} from '../api';
import { Badge, RoleBadge } from '../components/Badges';
import ListingStatusBadge from '../components/ListingStatusBadge';
import ConfirmDialog from '../components/ConfirmDialog';
import PageHeader from '../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/States';
import { useToast } from '../components/Toasts';
import { useLoader } from '../hooks';
import { PRODUCT_LABELS, ROLE_LABELS, actionLabel, formatDateTime, formatMoney } from '../labels';
import { PaymentStatusBadge } from './PaymentsPage';
import { listImportOrders } from '../../api/importOrders';
import StatusBadge from '../../components/importOrders/StatusBadge';
import { carTitle } from '../../constants/importOrders';
import { userIdFromToken } from '../../utils/jwt';

type Dialog = 'role' | 'block' | 'unblock' | 'logout' | null;

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div className="flex justify-between gap-4 py-2 border-b border-avtovo-border last:border-0 text-sm">
            <span className="text-avtovo-text-secondary">{label}</span>
            <span className="text-avtovo-text text-right break-all">{children}</span>
        </div>
    );
}

const card = 'bg-avtovo-card border border-avtovo-border rounded-xl p-5';
const actionButton = 'inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm border border-avtovo-border text-avtovo-text hover:bg-white/5 disabled:opacity-40 disabled:hover:bg-transparent';

/** Contact data is fetched only on request: every read is audited as USER_PII_VIEWED. */
function ProfileCard({ userId }: { userId: string }) {
    const [profile, setProfile] = useState<AdminUserProfile | null>(null);
    const [state, setState] = useState<'idle' | 'loading' | 'error' | 'missing'>('idle');

    const load = () => {
        setState('loading');
        getAdminUserProfile(userId)
            .then(p => { setProfile(p); setState('idle'); })
            .catch(e => setState((e as { response?: { status?: number } }).response?.status === 404 ? 'missing' : 'error'));
    };

    return (
        <section className={card}>
            <h2 className="font-semibold text-avtovo-text mb-3">Profil i dane kontaktowe</h2>
            {profile ? (
                <div>
                    <Field label="Telefon">{profile.phoneNumber || '—'}</Field>
                    <Field label="Adres">{[profile.street, profile.houseNumber].filter(Boolean).join(' ') || '—'}</Field>
                    <Field label="Miasto">{profile.city || '—'}</Field>
                    <Field label="Kraj">{profile.country || '—'}</Field>
                    <Field label="Liczba ogłoszeń">{profile.listingsCount ?? '—'}</Field>
                    {profile.bio && <p className="text-sm text-avtovo-text-secondary mt-3 whitespace-pre-line">{profile.bio}</p>}
                </div>
            ) : state === 'loading' ? <LoadingState /> : state === 'missing' ? <EmptyState label="Brak profilu w user-service" /> : (
                <div className="space-y-2">
                    <p className="text-sm text-avtovo-text-secondary">Odczyt danych osobowych zostanie zapisany w dzienniku zdarzeń.</p>
                    {state === 'error' && <p className="text-sm text-red-400">Nie udało się wczytać profilu.</p>}
                    <button onClick={load} className={actionButton}><Eye size={15} /> Pokaż dane kontaktowe</button>
                </div>
            )}
        </section>
    );
}

function ActivityCard({ userId }: { userId: string }) {
    const loader = useCallback(() => getAuditLog({ targetType: 'USER', targetId: userId, size: 10 }), [userId]);
    const { data, error, loading, reload } = useLoader(loader);
    return (
        <section className={card}>
            <div className="flex items-center justify-between mb-3">
                <h2 className="font-semibold text-avtovo-text">Historia działań administratorów</h2>
                <Link to={`/admin/dziennik?targetType=USER&targetId=${userId}`} className="text-sm text-avtovo-accent hover:underline">Wszystkie</Link>
            </div>
            {loading ? <LoadingState /> : error ? <ErrorState onRetry={reload} /> : !data?.content.length ? <EmptyState label="Brak wpisów" /> : (
                <ul className="space-y-3">
                    {data.content.map(e => (
                        <li key={e.id} className="text-sm">
                            <div className="flex justify-between gap-3">
                                <span className="text-avtovo-text">{actionLabel(e.action)}</span>
                                <span className="text-avtovo-text-secondary whitespace-nowrap">{formatDateTime(e.occurredAt)}</span>
                            </div>
                            <div className="text-xs text-avtovo-text-secondary">
                                {e.actorEmail ?? e.actorId}{e.reason ? ` · ${e.reason}` : ''}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

function ListingsCard({ userId }: { userId: string }) {
    const loader = useCallback(() => listAdminListings({ sellerId: userId, size: 5 }), [userId]);
    const { data, error, loading, reload } = useLoader(loader);
    return (
        <section className={card}>
            <div className="flex items-center justify-between mb-3">
                <h2 className="font-semibold text-avtovo-text">Ogłoszenia{data ? ` (${data.totalElements})` : ''}</h2>
                <Link to={`/admin/ogloszenia?sellerId=${userId}`} className="text-sm text-avtovo-accent hover:underline">Wszystkie</Link>
            </div>
            {loading ? <LoadingState /> : error ? <ErrorState onRetry={reload} /> : !data?.content.length ? <EmptyState label="Brak ogłoszeń" /> : (
                <ul className="space-y-2">
                    {data.content.map(l => (
                        <li key={l.id} className="flex items-center justify-between gap-3 text-sm">
                            <Link to={`/admin/ogloszenia/${l.id}`} className="text-avtovo-text hover:text-avtovo-accent">{l.make} {l.model} {l.year}</Link>
                            <ListingStatusBadge status={l.status} />
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

function PaymentsCard({ userId }: { userId: string }) {
    const loader = useCallback(() => listAdminPayments({ userId, size: 5 }), [userId]);
    const { data, error, loading, reload } = useLoader(loader);
    return (
        <section className={card}>
            <div className="flex items-center justify-between mb-3">
                <h2 className="font-semibold text-avtovo-text">Płatności{data ? ` (${data.totalElements})` : ''}</h2>
                <Link to={`/admin/platnosci?userId=${userId}`} className="text-sm text-avtovo-accent hover:underline">Wszystkie</Link>
            </div>
            {loading ? <LoadingState /> : error ? <ErrorState label="Dane niedostępne" onRetry={reload} /> : !data?.content.length ? <EmptyState label="Brak płatności" /> : (
                <ul className="space-y-2">
                    {data.content.map(p => (
                        <li key={p.id} className="flex items-center justify-between gap-3 text-sm">
                            <Link to={`/admin/platnosci/${p.id}`} className="text-avtovo-text hover:text-avtovo-accent">
                                {PRODUCT_LABELS[p.productCode] ?? p.productCode} · {formatMoney(p.amount, p.currency)}
                            </Link>
                            <PaymentStatusBadge status={p.status} />
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

function ImportOrdersCard({ dealerId }: { dealerId: string }) {
    const loader = useCallback(() => listImportOrders({ dealerId, size: 5 }), [dealerId]);
    const { data, error, loading, reload } = useLoader(loader);
    return (
        <section className={card}>
            <div className="flex items-center justify-between mb-3">
                <h2 className="font-semibold text-avtovo-text">Zlecenia importu{data ? ` (${data.totalElements})` : ''}</h2>
                <Link to={`/admin/zlecenia?dealerId=${dealerId}`} className="text-sm text-avtovo-accent hover:underline">Wszystkie</Link>
            </div>
            {loading ? <LoadingState /> : error ? <ErrorState label="Dane niedostępne" onRetry={reload} /> : !data?.content.length ? <EmptyState label="Brak zleceń" /> : (
                <ul className="space-y-2">
                    {data.content.map(o => (
                        <li key={o.id} className="flex items-center justify-between gap-3 text-sm">
                            <Link to={`/panel-dealera/zlecenia/${o.id}`} className="text-avtovo-text hover:text-avtovo-accent">{carTitle(o)}</Link>
                            <StatusBadge status={o.status} />
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

/** /admin/uzytkownicy/:id */
export default function UserDetailPage() {
    const { id = '' } = useParams();
    const toast = useToast();
    const loader = useCallback(() => getAdminUser(id), [id]);
    const { data: user, error, loading, reload } = useLoader(loader);
    const [dialog, setDialog] = useState<Dialog>(null);
    const [newRole, setNewRole] = useState<Role>('USER');
    const [activityKey, setActivityKey] = useState(0);
    const self = userIdFromToken(localStorage.getItem('access_token')) === id;

    const done = (message: string) => {
        toast.success(message);
        reload();
        setActivityKey(k => k + 1);
    };

    if (loading) return <LoadingState />;
    if (error || !user) {
        return <ErrorState label={(error as { response?: { status?: number } })?.response?.status === 404
            ? 'Nie ma takiego użytkownika.' : 'Nie udało się wczytać użytkownika.'} onRetry={reload} />;
    }

    return (
        <>
            <Link to="/admin/uzytkownicy" className="inline-flex items-center gap-1 text-sm text-avtovo-text-secondary hover:text-avtovo-text mb-3">
                <ArrowLeft size={14} /> Użytkownicy
            </Link>
            <PageHeader
                title={user.displayName || user.email}
                subtitle={user.email}
                actions={
                    <>
                        <button className={actionButton} disabled={self} title={self ? 'Nie możesz zmienić własnej roli' : undefined}
                                onClick={() => { setNewRole(user.role); setDialog('role'); }}>
                            <UserCog size={15} /> Zmień rolę
                        </button>
                        {user.active ? (
                            <button className={actionButton} disabled={self} title={self ? 'Nie możesz zablokować siebie' : undefined}
                                    onClick={() => setDialog('block')}>
                                <ShieldOff size={15} className="text-red-400" /> Zablokuj
                            </button>
                        ) : (
                            <button className={actionButton} onClick={() => setDialog('unblock')}>
                                <ShieldCheck size={15} className="text-green-400" /> Odblokuj
                            </button>
                        )}
                        <button className={actionButton} onClick={() => setDialog('logout')}>
                            <LogOut size={15} /> Wyloguj ze wszystkich urządzeń
                        </button>
                    </>
                }
            />

            <div className="grid gap-4 lg:grid-cols-2">
                <section className={card}>
                    <h2 className="font-semibold text-avtovo-text mb-3">Konto</h2>
                    <Field label="Rola"><RoleBadge role={user.role} /></Field>
                    <Field label="Status">{user.active ? <Badge tone="green">Aktywne</Badge> : <Badge tone="red">Zablokowane</Badge>}</Field>
                    <Field label="Logowanie przez">{user.providers.join(', ') || '—'}</Field>
                    <Field label="Rejestracja">{formatDateTime(user.createdAt)}</Field>
                    <Field label="Ostatnie logowanie">{formatDateTime(user.lastLoginAt)}</Field>
                    <Field label="ID">{user.id}</Field>
                </section>
                <ProfileCard userId={user.id} />
                <ListingsCard userId={user.id} />
                <PaymentsCard userId={user.id} />
                {user.role !== 'USER' && <ImportOrdersCard dealerId={user.id} />}
                <ActivityCard key={activityKey} userId={user.id} />
            </div>

            {dialog === 'role' && (
                <ConfirmDialog
                    title="Zmień rolę"
                    message={`Nowa rola obowiązuje od razu: bieżące sesje użytkownika odświeżą token.`}
                    confirmLabel="Zmień rolę"
                    confirmDisabled={newRole === user.role}
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await changeUserRole(user.id, newRole, reason);
                        done('Rola została zmieniona.');
                    }}
                >
                    <label className="block space-y-1.5">
                        <span className="text-sm text-avtovo-text">Nowa rola</span>
                        <select value={newRole} onChange={e => setNewRole(e.target.value as Role)}
                                className="w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text">
                            {(Object.keys(ROLE_LABELS) as Role[]).map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                        </select>
                    </label>
                </ConfirmDialog>
            )}
            {dialog === 'block' && (
                <ConfirmDialog
                    title="Zablokuj konto"
                    message="Użytkownik zostanie natychmiast wylogowany, jego aktywne ogłoszenia zostaną zawieszone, a czat wyłączony."
                    confirmLabel="Zablokuj"
                    destructive
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await blockUser(user.id, reason);
                        done('Konto zostało zablokowane.');
                    }}
                />
            )}
            {dialog === 'unblock' && (
                <ConfirmDialog
                    title="Odblokuj konto"
                    message="Zawieszone ogłoszenia wrócą do wyników wyszukiwania."
                    confirmLabel="Odblokuj"
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await unblockUser(user.id, reason);
                        done('Konto zostało odblokowane.');
                    }}
                />
            )}
            {dialog === 'logout' && (
                <ConfirmDialog
                    title="Wyloguj ze wszystkich urządzeń"
                    message="Wszystkie sesje użytkownika zostaną zakończone; będzie musiał zalogować się ponownie."
                    confirmLabel="Wyloguj"
                    destructive
                    onClose={() => setDialog(null)}
                    onConfirm={async reason => {
                        await logoutUserEverywhere(user.id, reason);
                        done('Użytkownik został wylogowany ze wszystkich urządzeń.');
                    }}
                />
            )}
        </>
    );
}
