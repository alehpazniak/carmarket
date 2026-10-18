import { useEffect } from 'react';
import { AdvancedMarker, ColorScheme, Map, Pin, useMap } from '@vis.gl/react-google-maps';
import { Info, MapPinOff } from 'lucide-react';
import MapsProvider, { MapsUnavailable } from './MapsProvider';
import PlaceSearchInput, { type PickedPlace } from './PlaceSearchInput';
import { GOOGLE_MAPS_MAP_ID, type LatLng, POLAND_CENTER, POLAND_ZOOM } from './googleMaps';

/**
 * "Lokalizacja na mapie" in the dealer page editor: the address search fills street, postal code, city
 * and the coordinates; the marker can then be dragged (or the map clicked) for the exact spot.
 * Lazy-loaded, so the Maps script and libraries only load on the editor page.
 */
export default function DealerLocationPicker({ position, onChange, onAddress }: {
    position: LatLng | null;
    onChange: (position: LatLng | null) => void;
    onAddress: (address: Pick<PickedPlace, 'street' | 'postalCode' | 'city'>) => void;
}) {
    const pick = (p: PickedPlace) => {
        onAddress({ street: p.street, postalCode: p.postalCode, city: p.city });
        onChange({ lat: p.lat, lng: p.lng });
    };

    return (
        <div className="space-y-3">
            <MapsProvider fallback={<MapsUnavailable className="h-40" />}>
                <PlaceSearchInput mode="address" placeholder="Wyszukaj adres firmy, np. ul. Prosta 5, Warszawa" onSelect={pick} />
                <div className="h-72 rounded-lg overflow-hidden border border-avtovo-border">
                    <Map
                        mapId={GOOGLE_MAPS_MAP_ID}
                        colorScheme={ColorScheme.DARK}
                        defaultCenter={position ?? POLAND_CENTER}
                        defaultZoom={position ? 16 : POLAND_ZOOM}
                        gestureHandling="cooperative"
                        streetViewControl={false}
                        mapTypeControl={false}
                        clickableIcons={false}
                        onClick={e => e.detail.latLng && onChange(e.detail.latLng)}
                    >
                        {position && (
                            <AdvancedMarker position={position} draggable title="Przeciągnij, aby poprawić położenie"
                                            onDragEnd={e => e.latLng && onChange({ lat: e.latLng.lat(), lng: e.latLng.lng() })}>
                                <Pin background="#3b82f6" borderColor="#1e3a8a" glyphColor="#ffffff" />
                            </AdvancedMarker>
                        )}
                        <FollowPosition position={position} />
                    </Map>
                </div>
            </MapsProvider>
            {position ? (
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-avtovo-text-secondary">
                    <span>
                        Przeciągnij znacznik lub kliknij mapę, aby wskazać dokładne miejsce.
                        {' '}({position.lat.toFixed(5)}, {position.lng.toFixed(5)})
                    </span>
                    <button type="button" onClick={() => onChange(null)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-avtovo-border text-avtovo-text-secondary hover:text-red-400 hover:border-red-400/60">
                        <MapPinOff size={14} /> Usuń z mapy
                    </button>
                </div>
            ) : (
                <p className="flex items-start gap-2 text-sm text-amber-400">
                    <Info size={16} className="mt-0.5 shrink-0" />
                    Twoja firma nie pojawi się na mapie dealerów. Wyszukaj adres lub kliknij mapę, aby ją dodać.
                </p>
            )}
        </div>
    );
}

/** Centers the map on a newly picked address; dragging keeps the view where it is. */
function FollowPosition({ position }: { position: LatLng | null }) {
    const map = useMap();
    const lat = position?.lat;
    const lng = position?.lng;
    useEffect(() => {
        if (!map || lat === undefined || lng === undefined) return;
        const bounds = map.getBounds();
        if (bounds?.contains({ lat, lng })) return;
        map.panTo({ lat, lng });
        if ((map.getZoom() ?? 0) < 14) map.setZoom(16);
    }, [map, lat, lng]);
    return null;
}
