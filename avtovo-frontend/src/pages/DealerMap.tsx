import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { AdvancedMarker, Circle, ColorScheme, InfoWindow, Map, Pin, useMap } from '@vis.gl/react-google-maps';
import { MarkerClusterer, type Marker } from '@googlemaps/markerclusterer';
import { Building2, Crosshair, ExternalLink, List, Loader2, Map as MapIcon, MapPin, Navigation } from 'lucide-react';
import { getNearbyDealers } from '../api/dealers';
import MapsProvider, { MapsUnavailable } from '../components/maps/MapsProvider';
import PlaceSearchInput from '../components/maps/PlaceSearchInput';
import {
    addressLine, directionsUrl, GOOGLE_MAPS_MAP_ID, type LatLng, POLAND_CENTER, POLAND_ZOOM,
} from '../components/maps/googleMaps';
import type { DealerMapItem } from '../types/dealers';

const RADII = [10, 25, 50, 100, 200];
const DEFAULT_RADIUS = 50;
const DEBOUNCE_MS = 300;

function parseCoord(value: string | null, max: number): number | null {
    if (value === null || value.trim() === '') return null;
    const n = Number(value);
    return Number.isFinite(n) && Math.abs(n) <= max ? n : null;
}

function formatKm(km: number): string {
    return `${km.toLocaleString('pl-PL', { maximumFractionDigits: 1 })} km`;
}

/** The chosen point, radius and selected dealer live in the URL (?lat=&lng=&r=), so the view can be shared. */
function useMapQuery() {
    const [params, setParams] = useSearchParams();
    const lat = parseCoord(params.get('lat'), 90);
    const lng = parseCoord(params.get('lng'), 180);
    const center: LatLng | null = lat !== null && lng !== null ? { lat, lng } : null;
    const r = Number(params.get('r'));
    const radius = RADII.includes(r) ? r : DEFAULT_RADIUS;

    const update = useCallback((next: { center?: LatLng; radius?: number }) => {
        setParams(prev => {
            const p = new URLSearchParams(prev);
            if (next.center) {
                p.set('lat', next.center.lat.toFixed(5));
                p.set('lng', next.center.lng.toFixed(5));
            }
            if (next.radius) p.set('r', String(next.radius));
            return p;
        }, { replace: true });
    }, [setParams]);

    return { center, radius, update };
}

type FetchState =
    | { status: 'idle' }
    | { status: 'loading'; key: string }
    | { status: 'ready'; key: string; dealers: DealerMapItem[] }
    | { status: 'error'; key: string; message: string };

/** /dealerzy — dealers on a map within a radius of a city, an address or the user's own location. */
export default function DealerMap() {
    useEffect(() => {
        document.title = 'Dealerzy na mapie — avtovo';
    }, []);

    return (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-4">
            <div>
                <h1 className="text-2xl font-bold text-avtovo-text">Dealerzy na mapie</h1>
                <p className="text-sm text-avtovo-text-secondary">Znajdź komis lub dealera w pobliżu — wpisz miasto albo użyj swojej lokalizacji.</p>
            </div>
            <MapsProvider fallback={<MapsUnavailable className="h-[60vh]" />}>
                <DealerMapContent />
            </MapsProvider>
        </div>
    );
}

function DealerMapContent() {
    const { center, radius, update } = useMapQuery();
    const [fetchState, setFetchState] = useState<FetchState>({ status: 'idle' });
    const [selected, setSelected] = useState<string | null>(null);
    const [view, setView] = useState<'map' | 'list'>('map');
    const [geo, setGeo] = useState<'idle' | 'locating' | 'denied' | 'error'>('idle');
    const lat = center?.lat;
    const lng = center?.lng;
    const key = lat !== undefined && lng !== undefined ? `${lat}|${lng}|${radius}` : null;

    // Debounced: clicking through radii or quick re-picks send one request
    useEffect(() => {
        if (lat === undefined || lng === undefined) return;
        const key = `${lat}|${lng}|${radius}`;
        const controller = new AbortController();
        const timer = setTimeout(() => {
            setFetchState({ status: 'loading', key });
            getNearbyDealers(lat, lng, radius, 200, controller.signal)
                .then(dealers => setFetchState({ status: 'ready', key, dealers }))
                .catch(err => {
                    if (axios.isCancel(err)) return;
                    console.error(err);
                    const limited = axios.isAxiosError(err) && err.response?.status === 429;
                    setFetchState({
                        status: 'error', key,
                        message: limited ? 'Zbyt wiele wyszukiwań. Spróbuj ponownie za chwilę.' : 'Nie udało się wczytać dealerów.',
                    });
                });
        }, DEBOUNCE_MS);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [lat, lng, radius]);

    // Results of an older point/radius aren't shown while the new ones load
    const current = key && fetchState.status !== 'idle' && fetchState.key === key ? fetchState : null;
    const dealers = useMemo(() => current?.status === 'ready' ? current.dealers : [], [current]);
    const loading = !!key && (!current || current.status === 'loading');
    const selectedDealer = dealers.find(d => d.slug === selected) ?? null;

    const choosePoint = (point: LatLng) => {
        setSelected(null);
        update({ center: point });
    };

    const locate = () => {
        if (!('geolocation' in navigator)) {
            setGeo('error');
            return;
        }
        setGeo('locating');
        navigator.geolocation.getCurrentPosition(
            pos => {
                setGeo('idle');
                choosePoint({ lat: pos.coords.latitude, lng: pos.coords.longitude });
            },
            err => setGeo(err.code === err.PERMISSION_DENIED ? 'denied' : 'error'),
            { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
        );
    };

    const nextRadius = RADII.find(r => r > radius);

    return (
        <div className="space-y-4">
            {/* Controls */}
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-4 space-y-3">
                <div className="flex flex-col md:flex-row gap-3">
                    <div className="flex-1">
                        <PlaceSearchInput placeholder="Wpisz miasto lub adres, np. Kraków" onSelect={p => choosePoint(p)} />
                    </div>
                    <button type="button" onClick={locate} disabled={geo === 'locating'}
                            className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg border border-avtovo-border text-sm text-avtovo-text hover:border-avtovo-accent disabled:opacity-50 whitespace-nowrap">
                        {geo === 'locating' ? <Loader2 size={15} className="animate-spin" /> : <Crosshair size={15} />}
                        Moja lokalizacja
                    </button>
                    <div className="flex items-center gap-1 overflow-x-auto" role="radiogroup" aria-label="Promień wyszukiwania">
                        {RADII.map(r => (
                            <button key={r} type="button" role="radio" aria-checked={r === radius}
                                    onClick={() => update({ radius: r })}
                                    className={`px-3 py-2 rounded-lg text-sm whitespace-nowrap border transition-colors ${r === radius
                                        ? 'bg-avtovo-accent border-avtovo-accent text-white'
                                        : 'border-avtovo-border text-avtovo-text-secondary hover:text-avtovo-text'}`}>
                                {r} km
                            </button>
                        ))}
                    </div>
                </div>
                {geo === 'denied' && <p className="text-sm text-amber-400">Brak dostępu do lokalizacji. Wpisz miasto w wyszukiwarce.</p>}
                {geo === 'error' && <p className="text-sm text-amber-400">Nie udało się ustalić Twojej lokalizacji. Wpisz miasto w wyszukiwarce.</p>}
            </div>

            {/* Mobile: list or map */}
            <div className="flex lg:hidden bg-avtovo-card border border-avtovo-border rounded-lg p-1 w-fit">
                {([['list', 'Lista', List], ['map', 'Mapa', MapIcon]] as const).map(([id, label, Icon]) => (
                    <button key={id} type="button" onClick={() => setView(id)}
                            className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-md text-sm ${view === id ? 'bg-avtovo-accent text-white' : 'text-avtovo-text-secondary'}`}>
                        <Icon size={15} /> {label}
                    </button>
                ))}
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_380px] gap-4">
                <div className={`${view === 'map' ? 'block' : 'hidden'} lg:block h-[60vh] lg:h-[70vh] rounded-xl overflow-hidden border border-avtovo-border relative`}>
                    <Map
                        mapId={GOOGLE_MAPS_MAP_ID}
                        colorScheme={ColorScheme.DARK}
                        defaultCenter={center ?? POLAND_CENTER}
                        defaultZoom={center ? 10 : POLAND_ZOOM}
                        gestureHandling="greedy"
                        streetViewControl={false}
                        mapTypeControl={false}
                        clickableIcons={false}
                        onClick={() => setSelected(null)}
                    >
                        {center && (
                            <>
                                <Circle center={center} radius={radius * 1000} clickable={false}
                                        strokeColor="#3b82f6" strokeOpacity={0.8} strokeWeight={2}
                                        fillColor="#3b82f6" fillOpacity={0.08} />
                                <AdvancedMarker position={center} title="Wybrany punkt" zIndex={0}>
                                    <div className="w-4 h-4 rounded-full bg-white border-4 border-avtovo-accent shadow" />
                                </AdvancedMarker>
                            </>
                        )}
                        <FitToRadius center={center} radius={radius} />
                        <DealerMarkers dealers={dealers} selected={selectedDealer} onSelect={setSelected} />
                    </Map>
                    {loading && (
                        <div className="absolute top-3 left-1/2 -translate-x-1/2 bg-avtovo-card/95 border border-avtovo-border rounded-full px-3 py-1.5 text-xs text-avtovo-text inline-flex items-center gap-2">
                            <Loader2 size={13} className="animate-spin text-avtovo-accent" /> Szukam dealerów…
                        </div>
                    )}
                </div>

                <div className={`${view === 'list' ? 'block' : 'hidden'} lg:block lg:h-[70vh] lg:overflow-y-auto space-y-2`}>
                    <DealerList
                        hasPoint={!!center} loading={loading} radius={radius} dealers={dealers}
                        error={current?.status === 'error' ? current.message : null}
                        selected={selected}
                        onSelect={slug => { setSelected(slug); setView('map'); }}
                        onWiden={nextRadius ? () => update({ radius: nextRadius }) : undefined}
                        nextRadius={nextRadius}
                    />
                </div>
            </div>
        </div>
    );
}

/** Zooms to the search circle whenever the point or the radius changes. */
function FitToRadius({ center, radius }: { center: LatLng | null; radius: number }) {
    const map = useMap();
    const lat = center?.lat;
    const lng = center?.lng;
    useEffect(() => {
        if (!map || lat === undefined || lng === undefined) return;
        const circle = new google.maps.Circle({ center: { lat, lng }, radius: radius * 1000 });
        const bounds = circle.getBounds();
        if (bounds) map.fitBounds(bounds, 24);
    }, [map, lat, lng, radius]);
    return null;
}

/** Clustered dealer markers; the selected one opens an InfoWindow and is panned into view. */
function DealerMarkers({ dealers, selected, onSelect }: {
    dealers: DealerMapItem[];
    selected: DealerMapItem | null;
    onSelect: (slug: string | null) => void;
}) {
    const map = useMap();
    const [markers, setMarkers] = useState<Record<string, google.maps.marker.AdvancedMarkerElement>>({});
    const clusterer = useMemo(() => (map ? new MarkerClusterer({ map }) : null), [map]);

    useEffect(() => {
        if (!clusterer) return;
        clusterer.clearMarkers();
        clusterer.addMarkers(Object.values(markers) as Marker[]);
    }, [clusterer, markers]);

    useEffect(() => () => clusterer?.setMap(null), [clusterer]);

    const setMarkerRef = useCallback((marker: google.maps.marker.AdvancedMarkerElement | null, slug: string) => {
        setMarkers(prev => {
            if ((marker && prev[slug] === marker) || (!marker && !prev[slug])) return prev;
            const next = { ...prev };
            if (marker) next[slug] = marker;
            else delete next[slug];
            return next;
        });
    }, []);

    // Zoom in far enough that the selected marker isn't hidden inside a cluster
    useEffect(() => {
        if (!map || !selected) return;
        map.panTo({ lat: selected.latitude, lng: selected.longitude });
        if ((map.getZoom() ?? 0) < 13) map.setZoom(13);
    }, [map, selected]);

    return (
        <>
            {dealers.map(d => (
                <DealerMarker key={d.slug} dealer={d} active={selected?.slug === d.slug}
                              setMarkerRef={setMarkerRef} onSelect={onSelect} />
            ))}
            {selected && markers[selected.slug] && (
                <InfoWindow anchor={markers[selected.slug]} onClose={() => onSelect(null)} headerDisabled
                            maxWidth={280}>
                    <DealerInfo dealer={selected} />
                </InfoWindow>
            )}
        </>
    );
}

/**
 * One marker. The ref callback must be stable: an inline one would be detached and re-attached on every
 * render, and each call changes the markers state, so the map would re-render forever.
 */
function DealerMarker({ dealer, active, setMarkerRef, onSelect }: {
    dealer: DealerMapItem;
    active: boolean;
    setMarkerRef: (marker: google.maps.marker.AdvancedMarkerElement | null, slug: string) => void;
    onSelect: (slug: string) => void;
}) {
    const { slug } = dealer;
    const ref = useCallback((marker: google.maps.marker.AdvancedMarkerElement | null) => setMarkerRef(marker, slug),
        [setMarkerRef, slug]);
    return (
        <AdvancedMarker ref={ref} position={{ lat: dealer.latitude, lng: dealer.longitude }} title={dealer.companyName}
                        onClick={() => onSelect(slug)} zIndex={active ? 10 : 1}>
            <Pin background={active ? '#f59e0b' : '#3b82f6'} borderColor="#1e3a8a" glyphColor="#ffffff" />
        </AdvancedMarker>
    );
}

/** InfoWindow content: Google draws a white bubble, so this uses dark text, not the avtovo theme. */
function DealerInfo({ dealer }: { dealer: DealerMapItem }) {
    const address = addressLine(dealer);
    return (
        <div className="text-gray-900 space-y-2 p-1 min-w-[200px]">
            <div className="flex items-center gap-2">
                <div className="w-10 h-10 rounded-md border border-gray-200 bg-white overflow-hidden flex items-center justify-center shrink-0">
                    {dealer.logoUrl
                        ? <img src={dealer.logoUrl} alt="" className="w-full h-full object-contain" />
                        : <Building2 size={18} className="text-gray-400" />}
                </div>
                <div className="min-w-0">
                    <p className="font-semibold leading-tight break-words">{dealer.companyName}</p>
                    <p className="text-xs text-gray-600">{formatKm(dealer.distanceKm)} od wybranego punktu</p>
                </div>
            </div>
            {address && <p className="text-xs text-gray-700">{address}</p>}
            <div className="flex gap-2 pt-1">
                <Link to={`/dealer/${dealer.slug}`}
                      className="inline-flex items-center gap-1 bg-blue-600 hover:bg-blue-700 text-white px-2.5 py-1.5 rounded-md text-xs font-medium">
                    <ExternalLink size={12} /> Zobacz stronę
                </Link>
                <a href={directionsUrl(dealer.latitude, dealer.longitude)} target="_blank" rel="noopener noreferrer"
                   className="inline-flex items-center gap-1 border border-gray-300 hover:border-blue-600 text-gray-900 px-2.5 py-1.5 rounded-md text-xs font-medium">
                    <Navigation size={12} /> Wyznacz trasę
                </a>
            </div>
        </div>
    );
}

function DealerList({ hasPoint, loading, error, radius, dealers, selected, onSelect, onWiden, nextRadius }: {
    hasPoint: boolean;
    loading: boolean;
    error: string | null;
    radius: number;
    dealers: DealerMapItem[];
    selected: string | null;
    onSelect: (slug: string) => void;
    onWiden?: () => void;
    nextRadius?: number;
}) {
    const refs = useRef<Record<string, HTMLButtonElement | null>>({});

    // A marker click scrolls its entry into view
    useEffect(() => {
        if (selected) refs.current[selected]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }, [selected]);

    if (!hasPoint) {
        return (
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 text-center text-sm text-avtovo-text-secondary">
                <MapPin size={28} className="mx-auto mb-2 text-avtovo-muted" />
                Wpisz miasto lub adres albo kliknij „Moja lokalizacja”, aby zobaczyć dealerów w okolicy.
            </div>
        );
    }
    if (loading) {
        return <div className="flex justify-center py-8"><Loader2 className="animate-spin text-avtovo-accent" /></div>;
    }
    if (error) {
        return <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 text-center text-sm text-red-400">{error}</div>;
    }
    if (dealers.length === 0) {
        return (
            <div className="bg-avtovo-card border border-avtovo-border rounded-xl p-6 text-center space-y-3">
                <p className="text-avtovo-text">Brak dealerów w promieniu {radius} km</p>
                {onWiden && nextRadius && (
                    <button type="button" onClick={onWiden}
                            className="inline-flex items-center gap-2 bg-avtovo-accent hover:bg-avtovo-accent-hover text-white px-4 py-2 rounded-lg text-sm font-medium">
                        Zwiększ promień do {nextRadius} km
                    </button>
                )}
            </div>
        );
    }

    return (
        <>
            <p className="text-sm text-avtovo-text-secondary px-1">
                {dealers.length === 1 ? '1 dealer' : `${dealers.length} dealerów`} w promieniu {radius} km
            </p>
            {dealers.map(d => {
                const address = addressLine(d);
                const active = d.slug === selected;
                return (
                    <div key={d.slug}
                         className={`bg-avtovo-card border rounded-xl p-3 transition-colors ${active ? 'border-avtovo-accent' : 'border-avtovo-border hover:border-gray-600'}`}>
                        <button type="button" ref={el => { refs.current[d.slug] = el; }} onClick={() => onSelect(d.slug)}
                                className="w-full flex items-start gap-3 text-left">
                            <div className="w-12 h-12 rounded-lg border border-avtovo-border bg-avtovo-bg overflow-hidden flex items-center justify-center shrink-0">
                                {d.logoUrl
                                    ? <img src={d.logoUrl} alt="" className="w-full h-full object-contain bg-white" />
                                    : <Building2 size={20} className="text-avtovo-muted" />}
                            </div>
                            <div className="min-w-0 flex-1">
                                <p className="text-avtovo-text font-medium truncate">{d.companyName}</p>
                                {address && <p className="text-xs text-avtovo-text-secondary truncate">{address}</p>}
                                <p className="text-xs text-avtovo-accent mt-0.5">{formatKm(d.distanceKm)}</p>
                            </div>
                        </button>
                        <div className="flex gap-3 mt-2 pl-[60px] text-xs">
                            <Link to={`/dealer/${d.slug}`} className="text-avtovo-accent hover:underline">Zobacz stronę</Link>
                            <a href={directionsUrl(d.latitude, d.longitude)} target="_blank" rel="noopener noreferrer"
                               className="text-avtovo-text-secondary hover:text-avtovo-text">Wyznacz trasę</a>
                        </div>
                    </div>
                );
            })}
        </>
    );
}
