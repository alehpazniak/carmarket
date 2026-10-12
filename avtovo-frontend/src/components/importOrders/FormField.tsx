import type { ReactNode } from 'react';

export const inputClass =
    'w-full bg-avtovo-bg border border-avtovo-border rounded-lg px-3 py-2 text-sm text-avtovo-text placeholder:text-avtovo-muted focus:outline-none focus:border-avtovo-accent disabled:opacity-60';

export function Field({ label, hint, children, className = '' }: { label: string; hint?: ReactNode; children: ReactNode; className?: string }) {
    return (
        <label className={`block space-y-1 ${className}`}>
            <span className="text-xs text-avtovo-text-secondary">{label}</span>
            {children}
            {hint && <span className="block text-xs text-avtovo-muted">{hint}</span>}
        </label>
    );
}

/** A yes / no / unknown select for nullable booleans (title flags). */
export function TriStateSelect({ value, onChange }: { value: boolean | null; onChange: (v: boolean | null) => void }) {
    return (
        <select
            value={value == null ? '' : String(value)}
            onChange={e => onChange(e.target.value === '' ? null : e.target.value === 'true')}
            className={inputClass}
        >
            <option value="">Nie wiadomo</option>
            <option value="true">Tak</option>
            <option value="false">Nie</option>
        </select>
    );
}

export function Section({ title, children, actions }: { title: string; children: ReactNode; actions?: ReactNode }) {
    return (
        <section className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 sm:p-5 space-y-4">
            <div className="flex items-center justify-between gap-3">
                <h2 className="font-semibold text-avtovo-text">{title}</h2>
                {actions}
            </div>
            {children}
        </section>
    );
}
