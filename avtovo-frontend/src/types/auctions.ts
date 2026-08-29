export type AuctionSource = 'COPART' | 'IAAI';
export type LotStatus = 'LIVE' | 'SOLD' | 'UNSOLD' | 'EXPIRED' | 'REMOVED';

export interface AuctionLot {
    id: string;
    vin: string;
    make: string;
    model: string;
    year: number;
    source: AuctionSource;
    lot_number: string;
    auctionPrice?: number;
    buyNowPrice?: number;
    currency: string;
    damageType?: string;
    primaryDamage?: string;
    secondaryDamage?: string;
    odometer?: number;
    odometerUnit?: string;
    engineCapacity?: number;
    fuelType?: string;
    transmission?: string;
    auctionLocation?: string;
    auctionDate?: string;
    saleDate?: string;
    status: LotStatus;
    imageUrls?: string[];
    createdAt?: string;
    updatedAt?: string;
}

export interface ImportCalculationResult {
    auctionPrice: number;
    auctionFee: number;
    us_delivery: number;
    oceanFreight: number;
    euPortFee: number;
    excise: number;
    vat: number;
    customsClearance: number;
    euDelivery: number;
    totalPln: number;
    totalUsd: number;
    estimatedRepairCostPln?: number;
    targetSalePricePln: number;
    estimatedProfitPln: number;
    profitMarginPercent: number;
    profitRating: string;
}

// Apibara live-search result — one entry from GET /api/auctions/apibara/vehicles
export interface ApibaraVehicle {
    platform: string;
    lotNumber: string;
    vin: string;
    title: string;
    year: number;
    make: string;
    model: string;
    auction?: { state: string; auctionAt?: string };
    pricing?: { currentBidUsd?: number; buyNowUsd?: number };
    location?: { display?: string; sendFrom?: string };
    condition?: { primaryDamage?: string; hasKey?: boolean };
    odometer?: { mi?: number };
    media?: { thumbsCount?: number; hasVideo?: boolean };
}

export interface ApibaraSearchResult {
    data: ApibaraVehicle[];
}

// GET /api/auctions/apibara/vehicles/{slugVin}
export interface ApibaraVehicleDetail {
    ok: boolean;
    data: {
        slugVin: string;
        vin: string;
        platform: string;
        lotNumber: string;
        title: string;
        year: number;
        make: string;
        model: string;
        type?: string;
        auction?: { state: string; formatted?: string; auctionAt?: string; isTimed?: boolean; isBuyNow?: boolean };
        pricing?: {
            current_bid_usd?: number;
            buy_now_usd?: number;
            last_sold_price_usd?: number;
            estimated_cost?: { from?: number; to?: number; text?: string };
        };
        location?: { display?: string; send_from?: string; state?: string };
        seller?: { name?: string; type?: string };
        condition?: {
            run_condition?: { value?: string; label?: string };
            has_key?: boolean;
            primary_damage?: string;
            secondary_damage?: string;
        };
        odometer?: { mi?: number; km?: number };
        vehicle_specs?: {
            exterior_color?: string;
            engine?: { raw?: string; size_l?: string; hp?: number };
            transmission?: string;
            fuel_type?: string;
            drive_type?: string;
            body_style?: string;
        };
        sale_document?: { name?: string; type?: string; export?: boolean; registration?: boolean };
        media?: {
            thumbs_count?: number;
            has_video?: boolean;
            has_360?: boolean;
            thumbs?: string[];
            items?: { type: string; thumb?: string; large?: string; url?: string }[];
        };
    } | null;
}

// GET /api/auctions/apibara/shipping/auction-to-port and .../vehicles/{slugVin}/shipping
export interface ApibaraShippingResult {
    ok: boolean;
    data: {
        vehicle: { platform: string; lotNumber: string; vin: string; title: string; type?: string };
        auctionLocation: { display?: string; facility_id?: string; matched_location_id?: string; match_score?: number };
        shipping: {
            recommended_port?: string;
            recommended_price_usd?: number;
            has_shipping_price: boolean;
            available_ports: { port: string; price: number }[];
        };
    } | null;
}

// GET /api/auctions/apibara/vehicles/{slugVin}/history — raw Apibara passthrough (snake_case, unmapped).
// Field names are the API's own — kept loose since the backend forwards the JSON as-is.
export interface ApibaraHistoryEntry {
    status?: string;
    platform?: string;
    sale_date?: string;
    auction_at?: string;
    price_usd?: number;
    current_bid_usd?: number;
    [key: string]: unknown;
}

export interface ApibaraHistoryResult {
    data?: ApibaraHistoryEntry[] | { items?: ApibaraHistoryEntry[] };
}

// GET /api/auctions/apibara/vehicles/{slugVin}/related — raw Apibara passthrough, grouped by relation.
export interface ApibaraRelatedVehicle {
    slug_vin?: string;
    vin?: string;
    lot_number?: string;
    title?: string;
    year?: number;
    make?: string;
    model?: string;
    platform?: string;
    location?: { display?: string };
    condition?: { primary_damage?: string };
    pricing?: { current_bid_usd?: number; last_sold_price_usd?: number };
    media?: { thumbs?: string[] };
    [key: string]: unknown;
}

export interface ApibaraRelatedResult {
    data?: {
        source?: ApibaraRelatedVehicle[];
        upcoming?: ApibaraRelatedVehicle[];
        past?: ApibaraRelatedVehicle[];
    };
}
