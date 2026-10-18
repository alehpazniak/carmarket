import { APIProvider } from '@vis.gl/react-google-maps';
import { MapPinOff } from 'lucide-react';
import { GOOGLE_MAPS_API_KEY } from './googleMaps';

/**
 * Loads the Google Maps script (only on pages that render this) with Polish labels. Every map uses the
 * same options: the loader can only be configured once per page load.
 */
export default function MapsProvider({ children, fallback }: { children: React.ReactNode; fallback?: React.ReactNode }) {
    if (!GOOGLE_MAPS_API_KEY) return <>{fallback ?? <MapsUnavailable />}</>;
    return (
        <APIProvider apiKey={GOOGLE_MAPS_API_KEY} language="pl" region="PL"
                     onError={err => console.error('Google Maps failed to load', err)}>
            {children}
        </APIProvider>
    );
}

export function MapsUnavailable({ className = '' }: { className?: string }) {
    return (
        <div className={`flex flex-col items-center justify-center gap-2 bg-avtovo-card border border-avtovo-border rounded-xl p-6 text-center ${className}`}>
            <MapPinOff size={28} className="text-avtovo-muted" />
            <p className="text-sm text-avtovo-text">Mapa jest chwilowo niedostępna.</p>
            <p className="text-xs text-avtovo-muted">Brak klucza Google Maps (VITE_GOOGLE_MAPS_API_KEY).</p>
        </div>
    );
}
