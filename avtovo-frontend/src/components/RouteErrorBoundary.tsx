import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { AlertTriangle, RotateCw } from 'lucide-react';

interface BoundaryProps {
    /** A new value clears a caught error, so navigating away from a broken page recovers the app. */
    resetKey: string;
    children: ReactNode;
}

interface BoundaryState {
    error: Error | null;
}

class ErrorBoundary extends Component<BoundaryProps, BoundaryState> {
    state: BoundaryState = { error: null };

    static getDerivedStateFromError(error: Error): BoundaryState {
        return { error };
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error('Render error', error, info.componentStack);
    }

    componentDidUpdate(prev: BoundaryProps) {
        if (this.state.error && prev.resetKey !== this.props.resetKey) {
            this.setState({ error: null });
        }
    }

    render() {
        if (!this.state.error) return this.props.children;
        return (
            <div className="max-w-md mx-auto px-4 py-16 text-center">
                <AlertTriangle size={40} className="mx-auto text-avtovo-accent mb-4" />
                <h1 className="text-avtovo-text text-xl font-semibold mb-6">Coś poszło nie tak</h1>
                <button
                    onClick={() => window.location.reload()}
                    className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white rounded-lg px-4 py-2 text-sm font-medium transition-colors"
                >
                    <RotateCw size={15} /> Odśwież stronę
                </button>
            </div>
        );
    }
}

/** Catches render errors below the navbar; resets when the route changes. */
export default function RouteErrorBoundary({ children }: { children: ReactNode }) {
    const location = useLocation();
    return <ErrorBoundary resetKey={location.pathname}>{children}</ErrorBoundary>;
}
