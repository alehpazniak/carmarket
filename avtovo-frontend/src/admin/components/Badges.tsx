import type { Role } from '../api';
import { ROLE_LABELS } from '../labels';

const ROLE_STYLES: Record<Role, string> = {
    ADMIN: 'bg-purple-500/15 text-purple-300 border-purple-700',
    DEALER: 'bg-blue-500/15 text-blue-300 border-blue-700',
    USER: 'bg-white/5 text-avtovo-text-secondary border-avtovo-border',
};

export function RoleBadge({ role }: { role: Role }) {
    return <span className={`inline-block px-2 py-0.5 rounded-md border text-xs font-medium ${ROLE_STYLES[role]}`}>{ROLE_LABELS[role]}</span>;
}

export function Badge({ tone, children }: { tone: 'red' | 'yellow' | 'green' | 'gray'; children: React.ReactNode }) {
    const styles = {
        red: 'bg-red-500/15 text-red-400 border-red-700',
        yellow: 'bg-yellow-500/15 text-yellow-300 border-yellow-700',
        green: 'bg-green-500/15 text-green-400 border-green-700',
        gray: 'bg-white/5 text-avtovo-text-secondary border-avtovo-border',
    }[tone];
    return <span className={`inline-block px-2 py-0.5 rounded-md border text-xs font-medium ${styles}`}>{children}</span>;
}
