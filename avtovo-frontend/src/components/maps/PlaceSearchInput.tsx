import { useEffect, useId, useRef, useState } from 'react';
import { useMapsLibrary } from '@vis.gl/react-google-maps';
import { Loader2, MapPin, Search } from 'lucide-react';

export interface PickedPlace {
    lat: number;
    lng: number;
    label: string;
    street: string | null;
    postalCode: string | null;
    city: string | null;
}

/** Places API (New) primary types: at most 5 per request. */
const CITY_AND_ADDRESS_TYPES = ['locality', 'postal_code', 'route', 'street_address', 'premise'];
const ADDRESS_TYPES = ['street_address', 'premise', 'route', 'subpremise'];
const MIN_QUERY = 2;

function component(place: google.maps.places.Place, ...types: string[]): string | null {
    for (const type of types) {
        const c = place.addressComponents?.find(ac => ac.types.includes(type));
        if (c?.longText) return c.longText;
    }
    return null;
}

function toPicked(place: google.maps.places.Place, fallbackLabel: string): PickedPlace | null {
    const loc = place.location;
    if (!loc) return null;
    const route = component(place, 'route');
    const number = component(place, 'street_number');
    return {
        lat: loc.lat(),
        lng: loc.lng(),
        label: place.formattedAddress ?? fallbackLabel,
        street: route ? [route, number].filter(Boolean).join(' ') : null,
        postalCode: component(place, 'postal_code'),
        city: component(place, 'locality', 'postal_town', 'administrative_area_level_3'),
    };
}

/**
 * Address search in Poland with Places API (New) autocomplete (AutocompleteSuggestion; the legacy
 * places.Autocomplete widget isn't available to new Google Cloud projects). Must be inside MapsProvider.
 * One session token per search, ended by the fetchFields call on the chosen place (billed as one session).
 */
export default function PlaceSearchInput({ onSelect, mode = 'cityOrAddress', placeholder, initialValue = '' }: {
    onSelect: (place: PickedPlace) => void;
    mode?: 'cityOrAddress' | 'address';
    placeholder?: string;
    initialValue?: string;
}) {
    const places = useMapsLibrary('places');
    const [input, setInput] = useState(initialValue);
    const [suggestions, setSuggestions] = useState<google.maps.places.AutocompleteSuggestion[]>([]);
    const [open, setOpen] = useState(false);
    const [active, setActive] = useState(-1);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const session = useRef<google.maps.places.AutocompleteSessionToken | null>(null);
    // Typing after a pick shouldn't reuse the pick's text as a query
    const skipNext = useRef(false);
    const listId = useId();

    useEffect(() => {
        if (!places || skipNext.current) {
            skipNext.current = false;
            return;
        }
        const query = input.trim();
        if (query.length < MIN_QUERY) return;
        let cancelled = false;
        const timer = setTimeout(() => {
            session.current ??= new places.AutocompleteSessionToken();
            places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
                input: query,
                sessionToken: session.current,
                includedRegionCodes: ['pl'],
                includedPrimaryTypes: mode === 'address' ? ADDRESS_TYPES : CITY_AND_ADDRESS_TYPES,
                language: 'pl',
                region: 'pl',
            })
                .then(res => {
                    if (cancelled) return;
                    setSuggestions(res.suggestions.filter(s => s.placePrediction));
                    setActive(-1);
                    setError(null);
                })
                .catch(err => {
                    console.error('Place autocomplete failed', err);
                    if (!cancelled) setError('Wyszukiwarka adresów jest chwilowo niedostępna.');
                });
        }, 250);
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [input, places, mode]);

    const pick = async (s: google.maps.places.AutocompleteSuggestion) => {
        const prediction = s.placePrediction;
        if (!prediction) return;
        const label = prediction.text.toString();
        skipNext.current = true;
        setInput(label);
        setOpen(false);
        setSuggestions([]);
        setBusy(true);
        try {
            const place = prediction.toPlace();
            await place.fetchFields({ fields: ['location', 'formattedAddress', 'addressComponents'] });
            const picked = toPicked(place, label);
            if (picked) onSelect(picked);
            else setError('Nie udało się ustalić położenia tego miejsca.');
        } catch (err) {
            console.error('Place details failed', err);
            setError('Nie udało się pobrać szczegółów adresu.');
        } finally {
            session.current = null;
            setBusy(false);
        }
    };

    // Older results stay in state while the query is too short; they just aren't shown
    const shown = input.trim().length >= MIN_QUERY ? suggestions : [];

    const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (!open || shown.length === 0) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setActive(i => (i + 1) % shown.length);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActive(i => (i <= 0 ? shown.length - 1 : i - 1));
        } else if (e.key === 'Enter') {
            // Never submit the surrounding form (dealer page editor) from the search box
            e.preventDefault();
            pick(shown[Math.max(active, 0)]);
        } else if (e.key === 'Escape') {
            setOpen(false);
        }
    };

    return (
        <div className="relative">
            <div className="relative">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-avtovo-muted pointer-events-none" />
                <input
                    type="text" value={input} placeholder={placeholder ?? 'Miasto lub adres'}
                    onChange={e => { setInput(e.target.value); setOpen(true); }}
                    onFocus={() => setOpen(true)}
                    onBlur={() => setTimeout(() => setOpen(false), 150)}
                    onKeyDown={onKeyDown}
                    role="combobox" aria-expanded={open && shown.length > 0} aria-controls={listId} aria-autocomplete="list"
                    className="w-full bg-avtovo-bg border border-avtovo-border rounded-lg pl-9 pr-9 py-2 text-sm text-avtovo-text placeholder:text-avtovo-muted focus:outline-none focus:border-avtovo-accent"
                />
                {busy && <Loader2 size={16} className="absolute right-3 top-1/2 -translate-y-1/2 animate-spin text-avtovo-accent" />}
            </div>
            {open && shown.length > 0 && (
                <ul id={listId} role="listbox"
                    className="absolute z-20 mt-1 w-full bg-avtovo-card border border-avtovo-border rounded-lg shadow-xl overflow-hidden">
                    {shown.map((s, i) => {
                        const p = s.placePrediction!;
                        return (
                            <li key={p.placeId} role="option" aria-selected={i === active}
                                onMouseDown={e => { e.preventDefault(); pick(s); }}
                                onMouseEnter={() => setActive(i)}
                                className={`flex items-start gap-2 px-3 py-2 text-sm cursor-pointer ${i === active ? 'bg-white/5' : ''}`}>
                                <MapPin size={14} className="mt-0.5 text-avtovo-muted shrink-0" />
                                <span className="min-w-0">
                                    <span className="block text-avtovo-text truncate">{p.mainText?.toString() ?? p.text.toString()}</span>
                                    {p.secondaryText && <span className="block text-xs text-avtovo-muted truncate">{p.secondaryText.toString()}</span>}
                                </span>
                            </li>
                        );
                    })}
                    <li className="px-3 py-1 text-[10px] text-right text-avtovo-muted">Powered by Google</li>
                </ul>
            )}
            {error && <p className="mt-1 text-xs text-red-400">{error}</p>}
        </div>
    );
}
