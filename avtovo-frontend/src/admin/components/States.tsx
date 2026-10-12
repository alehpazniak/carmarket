import { AlertTriangle, Inbox, Loader2, RotateCw } from 'lucide-react';

export function LoadingState({ label = 'Wczytywanie…' }: { label?: string }) {
    return (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-avtovo-text-secondary">
            <Loader2 size={18} className="animate-spin text-avtovo-accent" /> {label}
        </div>
    );
}

export function EmptyState({ label = 'Brak danych' }: { label?: string }) {
    return (
        <div className="flex flex-col items-center justify-center gap-2 py-10 text-sm text-avtovo-text-secondary">
            <Inbox size={24} className="text-avtovo-muted" /> {label}
        </div>
    );
}

export function ErrorState({ label = 'Nie udało się wczytać danych.', onRetry }: { label?: string; onRetry?: () => void }) {
    return (
        <div className="flex flex-col items-center justify-center gap-3 py-10 text-sm text-red-400">
            <span className="flex items-center gap-2"><AlertTriangle size={18} /> {label}</span>
            {onRetry && (
                <button onClick={onRetry}
                        className="inline-flex items-center gap-1.5 text-avtovo-text-secondary hover:text-avtovo-text">
                    <RotateCw size={14} /> Spróbuj ponownie
                </button>
            )}
        </div>
    );
}
