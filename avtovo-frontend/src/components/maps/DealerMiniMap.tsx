import { AdvancedMarker, ColorScheme, Map, Pin } from '@vis.gl/react-google-maps';
import { Navigation } from 'lucide-react';
import MapsProvider from './MapsProvider';
import { directionsUrl, GOOGLE_MAPS_MAP_ID } from './googleMaps';

/** The dealer's location on the "Kontakt" tab. Lazy-loaded; without a Maps key only the route link shows. */
export default function DealerMiniMap({ lat, lng, name }: { lat: number; lng: number; name: string }) {
    return (
        <div className="space-y-3">
            <MapsProvider fallback={null}>
                <div className="h-64 rounded-lg overflow-hidden border border-avtovo-border">
                    <Map
                        mapId={GOOGLE_MAPS_MAP_ID}
                        colorScheme={ColorScheme.DARK}
                        defaultCenter={{ lat, lng }}
                        defaultZoom={15}
                        gestureHandling="cooperative"
                        streetViewControl={false}
                        mapTypeControl={false}
                        clickableIcons={false}
                    >
                        <AdvancedMarker position={{ lat, lng }} title={name}>
                            <Pin background="#3b82f6" borderColor="#1e3a8a" glyphColor="#ffffff" />
                        </AdvancedMarker>
                    </Map>
                </div>
            </MapsProvider>
            <a href={directionsUrl(lat, lng)} target="_blank" rel="noopener noreferrer"
               className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-avtovo-accent text-avtovo-accent hover:bg-avtovo-accent/10 text-sm font-medium">
                <Navigation size={15} /> Wyznacz trasę
            </a>
        </div>
    );
}
