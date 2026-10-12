import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';

type Kind = 'success' | 'error';

interface Toast {
    id: number;
    kind: Kind;
    text: string;
}

interface ToastApi {
    success: (text: string) => void;
    error: (text: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);
let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);

    const push = useCallback((kind: Kind, text: string) => {
        const id = nextId++;
        setToasts(t => [...t, { id, kind, text }]);
        setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 4500);
    }, []);

    const [api] = useState<ToastApi>(() => ({
        success: text => push('success', text),
        error: text => push('error', text),
    }));

    return (
        <ToastContext.Provider value={api}>
            {children}
            <div className="fixed bottom-4 right-4 left-4 sm:left-auto z-[60] flex flex-col gap-2 sm:w-80" aria-live="polite">
                {toasts.map(t => (
                    <div key={t.id}
                         className={`flex items-start gap-2 rounded-lg border px-4 py-3 text-sm shadow-xl bg-avtovo-card ${t.kind === 'success'
                             ? 'border-green-700 text-green-300' : 'border-red-700 text-red-300'}`}>
                        {t.kind === 'success' ? <CheckCircle2 size={16} className="mt-0.5 shrink-0" /> : <XCircle size={16} className="mt-0.5 shrink-0" />}
                        <span>{t.text}</span>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
}

// eslint-disable-next-line react-refresh/only-export-components -- provider and its hook belong together
export function useToast(): ToastApi {
    const ctx = useContext(ToastContext);
    if (!ctx) throw new Error('useToast must be used within ToastProvider');
    return ctx;
}
