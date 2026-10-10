import type {CarDocument, CarListing} from '../types';

const isFuture = (value?: string | null) => !!value && new Date(value).getTime() > Date.now();

/** Search results carry search-service's `promoted` flag; listings from car-service only the end date. */
export function isPromoted(car: CarListing | CarDocument): boolean {
    return 'promoted' in car ? !!car.promoted : isFuture(car.promotedUntil);
}

export function isHighlighted(car: CarListing | CarDocument): boolean {
    return 'highlighted' in car ? !!car.highlighted : isFuture(car.highlightedUntil);
}

/** "18.10.2026" for an end date still in the future, otherwise null. */
export function activeUntilLabel(value?: string | null): string | null {
    return value && isFuture(value) ? new Date(value).toLocaleDateString('pl-PL') : null;
}
