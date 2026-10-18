/** Google Maps settings shared by the dealer map, the dealer page editor and the dealer's "Kontakt" tab. */

export const GOOGLE_MAPS_API_KEY: string = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || '';

/**
 * AdvancedMarker needs a map ID (Google Cloud → Map Management). Google's DEMO_MAP_ID works for
 * local testing, so a missing value only warns instead of breaking the map.
 */
export const GOOGLE_MAPS_MAP_ID: string = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID';

if (GOOGLE_MAPS_API_KEY && !import.meta.env.VITE_GOOGLE_MAPS_MAP_ID) {
    console.warn('VITE_GOOGLE_MAPS_MAP_ID is not set, falling back to DEMO_MAP_ID');
}

/** Before a point is chosen the map shows the whole country. */
export const POLAND_CENTER = { lat: 52.0, lng: 19.0 };
export const POLAND_ZOOM = 6;

export interface LatLng {
    lat: number;
    lng: number;
}

/** "Wyznacz trasę": Google Maps directions to the point, from wherever the user is. */
export function directionsUrl(lat: number, lng: number): string {
    return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
}

/** "ul. Prosta 5, 00-001 Warszawa" from a dealer's address fields; null when there is none. */
export function addressLine(a: { street: string | null; postalCode: string | null; city: string | null }): string | null {
    const cityLine = [a.postalCode, a.city].filter(Boolean).join(' ');
    return [a.street, cityLine].filter(Boolean).join(', ') || null;
}
