import api from './client';
import type { Page } from '../types';
import type {
    AuctionLot,
    ImportCalculationResult,
    ApibaraSearchResult,
    ApibaraVehicleDetail,
    ApibaraShippingResult,
    ApibaraHistoryResult,
    ApibaraRelatedResult,
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

export const getLot = (id: string) =>
    api.get<AuctionLot>(`/api/auctions/lots/${id}`).then(r => r.data);

export const getLotsByVin = (vin: string) =>
    api.get<AuctionLot[]>(`/api/auctions/lots/vin/${encodeURIComponent(vin)}`).then(r => r.data);

export const calculateImport = (
    id: string,
    data: { targetSalePricePln: number; estimatedRepairCostPln?: number; destinationCountry?: string }
) =>
    api.post<ImportCalculationResult>(`/api/auctions/lots/${id}/calculate`, data).then(r => r.data);

export const getComparables = (id: string) =>
    api.get<AuctionLot[]>(`/api/auctions/lots/${id}/comparables`).then(r => r.data);

// ── Live Apibara lookups — each call spends the account's monthly quota ──
export const searchApibaraVehicles = (params: Record<string, string | number> = {}) =>
    api.get<ApibaraSearchResult>(`/api/auctions/apibara/vehicles?${toQuery(params)}`).then(r => r.data);

export const getApibaraVehicle = (slugVin: string) =>
    api.get<ApibaraVehicleDetail>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}`).then(r => r.data);

export const getShippingEstimate = (params: { vin?: string; lot_number?: string; ports?: string }) =>
    api.get<ApibaraShippingResult>(`/api/auctions/apibara/shipping/auction-to-port?${toQuery(params)}`).then(r => r.data);

export const getVehicleHistory = (slugVin: string) =>
    api.get<ApibaraHistoryResult>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}/history`).then(r => r.data);

export const getRelatedVehicles = (slugVin: string) =>
    api.get<ApibaraRelatedResult>(`/api/auctions/apibara/vehicles/${encodeURIComponent(slugVin)}/related`).then(r => r.data);
