import axios from 'axios';
import api from './client';
import type { Page } from '../types';
import type {
    AuctionLot,
    LotFacets,
    ImportCalculationResult,
    MaxBidRequest,
    MaxBidResult,
    ApibaraSearchResult,
    ApibaraVehicleDetail,
    ApibaraShippingResult,
    ApibaraHistoryResult,
    ApibaraRelatedResult,
    LengthClass,
    PolandZone,
    W8Auction,
    W8Location,
    W8ShippingQuote,
} from '../types/auctions';

const toQuery = (params: object) =>
    new URLSearchParams(
        Object.entries(params as Record<string, string | number | undefined>)
            .filter(([, v]) => v !== undefined && v !== '')
            .map(([k, v]) => [k, String(v)])
    ).toString();

export interface LotSearchParams {
    make?: string;
    model?: string;
    yearFrom?: number;
    yearTo?: number;
    damageType?: string;
    page?: number;
    size?: number;
}

// ── Synced lots (our DB, kept fresh by the scheduled Apibara sync) ──
export const searchLots = (params: LotSearchParams = {}) =>
    api.get<Page<AuctionLot>>(`/api/auctions/lots?${toQuery(params)}`).then(r => r.data);

/** Makes/models that currently have listed lots, with counts, for the /aukcje dropdowns. */
export const getLotFacets = () =>
    api.get<LotFacets>('/api/auctions/lots/facets').then(r => r.data);

/** What GET /lots/lookup recognised in the search field on /aukcje. */
export type LotLookupKind = 'LOT_NUMBER' | 'VIN' | 'COPART_URL' | 'IAAI_URL' | 'URL' | 'UNKNOWN';

export interface LotLookupResult {
    kind: LotLookupKind;
    /** Our lots, best first (LIVE, then the latest auction); empty when we don't have the car */
    ids: string[];
    /** Lot number or VIN the live page can open directly; null for links (resolved through Apibara first) */
    identifier: string | null;
}

/** Lot number, VIN or Copart/IAAI link in our own database: free, no Apibara request. */
export const lookupLot = (q: string) =>
    api.get<LotLookupResult>(`/api/auctions/lots/lookup?${toQuery({ q })}`).then(r => r.data);

export const getLot = (id: string) =>
    api.get<AuctionLot>(`/api/auctions/lots/${id}`).then(r => r.data);

export const getLotsByVin = (vin: string) =>
    api.get<AuctionLot[]>(`/api/auctions/lots/vin/${encodeURIComponent(vin)}`).then(r => r.data);

export const calculateImport = (
    id: string,
    data: {
        targetSalePricePln: number;
        estimatedRepairCostPln?: number;
        destinationCountry?: string;
        suv?: boolean;
        polandZone?: PolandZone;
        lengthClass?: LengthClass;
    }
) =>
    api.post<ImportCalculationResult>(`/api/auctions/lots/${id}/calculate`, data).then(r => r.data);

export const calculateMaxBid = (id: string, data: MaxBidRequest) =>
    api.post<MaxBidResult>(`/api/auctions/lots/${id}/max-bid`, data).then(r => r.data);

// ── W8 Shipping transport calculator (logged-in users; our own price table, no Apibara quota) ──

export const getW8Locations = (q: string, auction?: W8Auction) =>
    api.get<W8Location[]>(`/api/auctions/shipping/w8/locations?${toQuery({ q, auction })}`).then(r => r.data);

/** Rejects with a 404 when W8 has no price for the location, not even for its state. */
export const getW8Quote = (params: {
    location: string;
    auction?: W8Auction;
    suv?: boolean;
    zone?: PolandZone;
    length?: LengthClass;
}) =>
    api.get<W8ShippingQuote>(`/api/auctions/shipping/w8/quote?${toQuery(params)}`).then(r => r.data);

/** Apibara's `platform` as a W8 auction, or undefined when W8 doesn't know it (the backend would reject it). */
export function w8AuctionFromPlatform(platform?: string): string | undefined {
    const p = (platform ?? '').toLowerCase();
    return ['copart', 'iaai', 'manheim', 'adesa'].includes(p) ? p : undefined;
}

export const getComparables = (id: string) =>
    api.get<AuctionLot[]>(`/api/auctions/lots/${id}/comparables`).then(r => r.data);

// ── Live Apibara lookups — each call spends the account's monthly quota ──
// Open to anonymous users; the gateway limits them per IP and all together (429), logged-in users far less.

/**
 * Why a live lookup failed, from auction-import-service (ApibaraController.LookupFailure). The gateway's own 429
 * (its per-IP / anonymous limits) has no body.
 */
export type ApibaraFailureReason = 'QUOTA_EXHAUSTED' | 'RATE_LIMITED' | 'UNAVAILABLE';

/**
 * Error text for a failed live lookup: Apibara's monthly quota, a short-term limit (or the gateway's 429: log in
 * when anonymous), Apibara not answering; anything else gets the fallback.
 */
export function apibaraErrorMessage(err: unknown, isAuthenticated: boolean, fallback: string): string {
    if (!axios.isAxiosError(err)) return fallback;
    const reason = (err.response?.data as { reason?: ApibaraFailureReason } | undefined)?.reason;
    if (reason === 'QUOTA_EXHAUSTED') {
        return 'Miesięczny limit zapytań do Apibara został wyczerpany. Auta z naszej bazy nadal możesz wyszukać.';
    }
    if (reason === 'UNAVAILABLE') {
        return 'Apibara chwilowo nie odpowiada.';
    }
    if (reason === 'RATE_LIMITED' || err.response?.status === 429) {
        return isAuthenticated
            ? 'Zbyt wiele zapytań do Apibara. Spróbuj ponownie za chwilę.'
            : 'Wyczerpano limit wyszukiwań dla niezalogowanych. Zaloguj się, aby kontynuować.';
    }
    return fallback;
}
export const searchApibaraVehicles = (params: Record<string, string | number> = {}) =>
    api.get<ApibaraSearchResult>(`/api/auctions/apibara/vehicles?${toQuery(params)}`).then(r => r.data);

export const getApibaraVehicle = (slugVin: string) =>
    api.get<ApibaraVehicleDetail>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}`).then(r => r.data);

/** A Copart/IAAI link → the vehicle (urltodetails, Copart lot fallback); 404 when Apibara doesn't know it. */
export const resolveApibaraUrl = (url: string) =>
    api.get<ApibaraVehicleDetail>(`/api/auctions/apibara/vehicles/url-to-details?${toQuery({ url })}`).then(r => r.data);

export const NOT_FOUND_MESSAGE = 'Nie znaleziono auta o tym numerze';

/** A live lookup's 404: neither our database nor Apibara knows the car. */
export const isNotFound = (err: unknown) => axios.isAxiosError(err) && err.response?.status === 404;

export const getShippingEstimate =(params: { vin?: string; lot_number?: string; ports?: string }) =>
    api.get<ApibaraShippingResult>(`/api/auctions/apibara/shipping/auction-to-port?${toQuery(params)}`).then(r => r.data);

export const getVehicleHistory = (slugVin: string) =>
    api.get<ApibaraHistoryResult>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}/history`).then(r => r.data);

export const getRelatedVehicles = (slugVin: string) =>
    api.get<ApibaraRelatedResult>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}/related`).then(r => r.data);

export const calculateApibaraMaxBid = (slugVin: string, data: MaxBidRequest) =>
    api.post<MaxBidResult>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}/max-bid`, data).then(r => r.data);
