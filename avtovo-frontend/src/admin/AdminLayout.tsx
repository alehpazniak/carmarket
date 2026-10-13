import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
    Activity, BellRing, ClipboardList, CreditCard, Flag, Gavel, LayoutDashboard, Menu, ScrollText, Car, Users, X,
} from 'lucide-react';

const ADMIN_SECTIONS = [
    { to: '/admin', label: 'Pulpit', icon: LayoutDashboard, end: true },
    { to: '/admin/uzytkownicy', label: 'Użytkownicy', icon: Users },
    { to: '/admin/ogloszenia', label: 'Ogłoszenia', icon: Car },
    { to: '/admin/zgloszenia', label: 'Zgłoszenia', icon: Flag },
    { to: '/admin/platnosci', label: 'Płatności', icon: CreditCard },
    { to: '/admin/zlecenia', label: 'Zlecenia importu', icon: ClipboardList },
    { to: '/admin/aukcje', label: 'Aukcje', icon: Gavel },
    { to: '/admin/powiadomienia', label: 'Powiadomienia', icon: BellRing },
    { to: '/admin/dziennik', label: 'Dziennik zdarzeń', icon: ScrollText },
    { to: '/admin/system', label: 'System', icon: Activity },
];

function SectionLinks({ onNavigate }: { onNavigate?: () => void }) {
    return (
        <nav className="space-y-1">
            {ADMIN_SECTIONS.map(({ to, label, icon: Icon, end }) => (
                <NavLink key={to} to={to} end={end} onClick={onNavigate}
                         className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${isActive
                             ? 'bg-avtovo-accent/15 text-avtovo-accent' : 'text-avtovo-text-secondary hover:text-avtovo-text hover:bg-white/5'}`}>
                    <Icon size={17} /> {label}
                </NavLink>
            ))}
        </nav>
    );
}

/** Sidebar on desktop, a drawer behind the "Menu" button on mobile. */
export default function AdminLayout() {
    const [drawerOpen, setDrawerOpen] = useState(false);

    return (
        <div className="max-w-7xl mx-auto px-4 py-6 lg:flex lg:gap-6">
            <aside className="hidden lg:block w-56 shrink-0">
                <p className="px-3 mb-3 text-xs uppercase tracking-wide text-avtovo-muted">Panel administratora</p>
                <SectionLinks />
            </aside>

            <div className="lg:hidden mb-4">
                <button onClick={() => setDrawerOpen(true)}
                        className="inline-flex items-center gap-2 bg-avtovo-card border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text">
                    <Menu size={16} /> Panel administratora
                </button>
            </div>
            {drawerOpen && (
                <div className="lg:hidden fixed inset-0 z-40 bg-black/70" onClick={() => setDrawerOpen(false)}>
                    <div className="absolute inset-y-0 left-0 w-72 max-w-[85%] bg-avtovo-card border-r border-avtovo-border p-4 space-y-4"
                         onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold text-avtovo-text">Panel administratora</span>
                            <button onClick={() => setDrawerOpen(false)} aria-label="Zamknij menu" className="text-avtovo-text-secondary">
                                <X size={18} />
                            </button>
                        </div>
                        <SectionLinks onNavigate={() => setDrawerOpen(false)} />
                    </div>
                </div>
            )}

            <main className="flex-1 min-w-0">
                <Outlet />
            </main>
        </div>
    );
}
