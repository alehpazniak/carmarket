import { useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { listDealerRequests, listUsers, type AdminUser, type DealerRequest } from '../api';
import { Badge, RoleBadge } from '../components/Badges';
import DataTable, { type Column } from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import SearchInput from '../components/SearchInput';
import { useLoader, useQueryState } from '../hooks';
import { ROLE_LABELS, formatDateTime } from '../labels';

const inputClass = 'bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text';

const userColumns: Column<AdminUser>[] = [
    {
        key: 'user', header: 'Użytkownik', sortField: 'email', render: u => (
            <div className="min-w-[12rem]">
                <div className="font-medium">{u.displayName || '—'}</div>
                <div className="text-xs text-avtovo-text-secondary break-all">{u.email}</div>
            </div>
        ),
    },
    {
        key: 'role', header: 'Rola', sortField: 'role', render: u => (
            <div className="flex flex-wrap gap-1">
                <RoleBadge role={u.role} />
                {!u.active && <Badge tone="red">Zablokowany</Badge>}
            </div>
        ),
    },
    { key: 'providers', header: 'Logowanie', render: u => <span className="text-avtovo-text-secondary">{u.providers.join(', ') || '—'}</span> },
    { key: 'created', header: 'Rejestracja', sortField: 'createdAt', render: u => <span className="whitespace-nowrap">{formatDateTime(u.createdAt)}</span> },
    { key: 'login', header: 'Ostatnie logowanie', sortField: 'lastLoginAt', render: u => <span className="whitespace-nowrap">{formatDateTime(u.lastLoginAt)}</span> },
];

const STATE_LABELS: Record<DealerRequest['state'], string> = { pending: 'Oczekujące', confirmed: 'Potwierdzone', expired: 'Wygasłe' };

const requestColumns: Column<DealerRequest>[] = [
    {
        key: 'user', header: 'Użytkownik', render: r => (
            <div>
                <div className="font-medium">{r.displayName || '—'}</div>
                <div className="text-xs text-avtovo-text-secondary break-all">{r.email ?? r.userId}</div>
            </div>
        ),
    },
    {
        key: 'state', header: 'Stan', render: r => (
            <Badge tone={r.state === 'confirmed' ? 'green' : r.state === 'pending' ? 'yellow' : 'gray'}>{STATE_LABELS[r.state]}</Badge>
        ),
    },
    { key: 'created', header: 'Wysłano', render: r => formatDateTime(r.createdAt) },
    { key: 'expires', header: 'Ważne do', render: r => formatDateTime(r.expiresAt) },
    { key: 'confirmed', header: 'Potwierdzono', render: r => formatDateTime(r.confirmedAt) },
];

function UserList() {
    const navigate = useNavigate();
    const { values, update, query } = useQueryState({ q: '', role: '', active: '', sort: 'createdAt,desc', page: '0' });
    const loader = useCallback(() => listUsers({
        q: values.q, role: values.role, active: values.active, sort: values.sort, page: Number(values.page) || 0, size: 20,
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }), [query]);
    const { data, error, loading, reload } = useLoader(loader);

    return (
        <>
            <div className="flex flex-wrap gap-2 mb-4">
                <SearchInput key={`q-${values.q}`} value={values.q} onChange={v => update({ q: v })} placeholder="E-mail lub nazwa" />
                <select value={values.role} onChange={e => update({ role: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie role</option>
                    {Object.entries(ROLE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
                <select value={values.active} onChange={e => update({ active: e.target.value })} className={inputClass}>
                    <option value="">Wszystkie konta</option>
                    <option value="true">Aktywne</option>
                    <option value="false">Zablokowane</option>
                </select>
            </div>
            <DataTable
                columns={userColumns}
                rows={data?.content}
                rowKey={u => u.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak użytkowników dla tych filtrów"
                onRowClick={u => navigate(`/admin/uzytkownicy/${u.id}`)}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
                sort={values.sort}
                onSortChange={sort => update({ sort })}
            />
        </>
    );
}

function DealerRequests() {
    const navigate = useNavigate();
    const { values, update, query } = useQueryState({ state: 'pending', page: '0' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    const loader = useCallback(() => listDealerRequests(values.state, Number(values.page) || 0), [query]);
    const { data, error, loading, reload } = useLoader(loader);

    return (
        <>
            <div className="flex gap-2 mb-4">
                <select value={values.state} onChange={e => update({ state: e.target.value })} className={inputClass}>
                    {Object.entries(STATE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
            </div>
            <DataTable
                columns={requestColumns}
                rows={data?.content}
                rowKey={r => r.id}
                loading={loading}
                error={error}
                onRetry={reload}
                emptyText="Brak próśb w tym stanie"
                onRowClick={r => navigate(`/admin/uzytkownicy/${r.userId}`)}
                page={data?.number}
                totalPages={data?.totalPages}
                totalElements={data?.totalElements}
                onPageChange={p => update({ page: String(p) })}
            />
        </>
    );
}

/** /admin/uzytkownicy — accounts (auth-service) and the USER → DEALER requests tab. */
export default function UsersPage() {
    const [params, setParams] = useSearchParams();
    const dealers = params.get('tab') === 'dealers';
    const tabClass = (active: boolean) => `px-3 py-2 text-sm border-b-2 -mb-px ${active
        ? 'border-avtovo-accent text-avtovo-text' : 'border-transparent text-avtovo-text-secondary hover:text-avtovo-text'}`;

    return (
        <>
            <PageHeader title="Użytkownicy" />
            <div className="flex gap-2 border-b border-avtovo-border mb-4">
                {/* Each tab starts with its own default filters */}
                <button className={tabClass(!dealers)} onClick={() => setParams({}, { replace: true })}>Konta</button>
                <button className={tabClass(dealers)} onClick={() => setParams({ tab: 'dealers' }, { replace: true })}>
                    Prośby o konto dealera
                </button>
            </div>
            {dealers ? <DealerRequests /> : <UserList />}
        </>
    );
}
