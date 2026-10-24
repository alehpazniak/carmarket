export type AuctionSource = 'COPART' | 'IAAI';
export type LotStatus = 'LIVE' | 'SOLD' | 'UNSOLD' | 'EXPIRED' | 'REMOVED';
export type FuelType = 'PETROL' | 'DIESEL' | 'ELECTRIC' | 'HYBRID' | 'PLUGIN_HYBRID';

// GET /api/auctions/lots, /lots/{id}, /lots/vin/{vin}, /lots/{id}/comparables — auction-import-service's
// AuctionLot entity, serialized as-is (camelCase, no @JsonProperty).
export interface AuctionLot {
    id: string;
    vin: string;
    make: string;
    model: string;
    year: number;
    source: AuctionSource;
    lotNumber: string;
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
    // As Apibara sends them: e.g. "RUNS AND DRIVES", "Run & Drive Verified"; "SALVAGE CERTIFICATE (FL)"
    runCondition?: string;
    titleType?: string;
    createdAt?: string;
    updatedAt?: string;
}

// GET /api/auctions/lots/facets — auction-import-service's LotFacetsResponse. Values are upper case and trimmed
// ("TOYOTA", "RAV4"), the form /lots matches exactly; makes and models are sorted alphabetically.
export interface LotModelFacet {
    model: string;
    count: number;
}

export interface LotMakeFacet {
    make: string;
    count: number;
    models: LotModelFacet[];
}

export interface LotFacets {
    makes: LotMakeFacet[];
}

// POST /api/auctions/lots/{id}/calculate — auction-import-service's ImportCalculationResponse (camelCase).
export interface ImportCalculationResult {
    auctionPrice: number;
    auctionFee: number;
    usDelivery: number;
    oceanFreight: number;
    euPortFee: number;
    excise: number;
    customsDuty: number;
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
    /** W8 Shipping's itemized quote behind the shipping lines; null when the flat rates were used. */
    w8?: W8ShippingQuote | null;
}

// POST /api/auctions/lots/{id}/max-bid and /apibara/vehicles/{slugVin}/max-bid — auction-import-service's
// MaxBidRequest / MaxBidResponse (camelCase). Reverse calculator: given a total budget, how much
// can the buyer afford to bid on the car itself once shipping, duty, excise, VAT and repair
// are all accounted for.
export interface MaxBidRequest {
    budgetPln: number;
    estimatedRepairCostPln?: number;
    engineCapacityCm3?: number;
    fuelType?: FuelType;
    suv?: boolean;
    /** For the customs duty by country of manufacture (US-built cars pay 0%); may be masked. */
    vin?: string;
    /** W8 quote inputs: Apibara's `platform` and `location.display` (the vehicle page sends them). */
    auction?: string;
    auctionLocation?: string;
    polandZone?: PolandZone;
    lengthClass?: LengthClass;
}

export interface MaxBidResult {
    budgetPln: number;
    estimatedRepairCostPln: number;
    shippingCostUsd: number;
    shippingCostPln: number;
    shippingDeliveryUsd: number;
    shippingDeliveryPln: number;
    exchangeRate: number;
    exciseRate: number;
    customsDutyRate: number;
    vatRate: number;
    excise: number;
    customsDuty: number;
    vat: number;
    maxCarPricePln: number;
    maxCarPriceUsd: number;
    budgetSufficient: boolean;
    /**
     * W8 Shipping's itemized quote; with it shippingCost* = auction → EU border (towing + ocean freight) and
     * shippingDelivery* = everything after the border including W8's fees.
     */
    w8?: W8ShippingQuote | null;
}

// W8 Shipping (our logistics partner) — auction-import-service's W8ShippingQuote and friends (camelCase).
export type W8Auction = 'COPART' | 'IAAI' | 'MANHEIM' | 'ADESA';
export type W8Port = 'SAVANNAH' | 'LOS_ANGELES' | 'NEW_YORK' | 'HOUSTON' | 'SEATTLE' | 'NORFOLK';
export type PolandZone = 'A' | 'B' | 'C';
export type LengthClass = 'STANDARD' | 'OVER_5M' | 'OVER_5_3M';
export type W8MatchType = 'EXACT' | 'OTHER_AUCTION' | 'STATE_ESTIMATE';
export type OceanFreightSource = 'W8_PRICE_LIST' | 'DERIVED' | 'ESTIMATE';

export interface W8PortOption {
    port: W8Port;
    portLabel: string;
    towingUsd: number;
    oceanFreightUsd: number;
    oceanFreightSource: OceanFreightSource;
    transitDaysMin?: number | null;
    transitDaysMax?: number | null;
    toEuBorderUsd: number;
}

export interface W8ShippingQuote {
    priceList: string;
    matchType: W8MatchType;
    /** W8's yard name, or the state for STATE_ESTIMATE. */
    matchedLocation: string;
    auction?: W8Auction | null;
    containerShare: 'QUARTER' | 'THIRD';
    /** Every port with a towing price, cheapest first; `chosen` is the first. */
    options: W8PortOption[];
    chosen: W8PortOption;
    forwarderEur: number;
    brokerEur: number;
    scanEur: number;
    polandZone: PolandZone;
    polandDeliveryPln: number;
    accessFeeUsd: number;
    dealerFeeUsd: number;
    toEuBorderUsd: number;
    afterBorderPln: number;
    w8FeesUsd: number;
    usdPlnRate: number;
    eurPlnRate: number;
}

// GET /api/auctions/shipping/w8/locations — autocomplete.
export interface W8Location {
    auction: W8Auction;
    location: string;
    state?: string | null;
    ports: { port: W8Port; towingUsd: number }[];
}

// GET /api/auctions/apibara/vehicles (one entry) — auction-import-service's ApibaraResponse.Vehicle,
// serialized with its @JsonProperty (snake_case) names.
export interface ApibaraVehicle {
    platform: string;
    lot_number: string;
    vin: string;
    title: string;
    year: number;
    make: string;
    model: string;
    auction?: { state: string; auction_at?: string };
    pricing?: { current_bid_usd?: number; buy_now_usd?: number };
    location?: { display?: string; send_from?: string };
    condition?: { primary_damage?: string; has_key?: boolean };
    odometer?: { mi?: number };
    media?: { thumbs_count?: number; has_video?: boolean; thumbs?: string[] };
}

// GET /api/auctions/apibara/vehicles — auction-import-service's ApibaraResponse.
export interface ApibaraSearchResult {
    data: ApibaraVehicle[];
}

// GET /api/auctions/apibara/vehicles/{slugVin} — auction-import-service's ApibaraVehicleDetailResponse,
// serialized with its @JsonProperty (snake_case) names.
export interface ApibaraVehicleDetail {
    ok: boolean;
    data: {
        slug_vin: string;
        vin: string;
        platform: string;
        lot_number: string;
        title: string;
        year: number;
        make: string;
        model: string;
        type?: string;
        auction?: { state: string; formatted?: string; auction_at?: string; is_timed?: boolean; is_buy_now?: boolean; timed_end_at?: string };
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

// GET /api/auctions/apibara/shipping/auction-to-port and .../vehicles/{slugVin}/shipping — auction-import-service's
// ApibaraShippingResponse, snake_case @JsonProperty names; @JsonInclude(NON_NULL), so any field may be absent.
export interface ApibaraShippingResult {
    ok: boolean;
    data?: {
        vehicle?: { platform?: string; lot_number?: string; vin?: string; title?: string; type?: string };
        auction_location?: { display?: string; facility_id?: string; matched_location_id?: string; match_score?: number };
        shipping?: {
            recommended_port?: string;
            recommended_price_usd?: number;
            has_shipping_price?: boolean;
            available_ports?: { port: string; price: number }[];
        };
    };
}

// GET /api/auctions/apibara/vehicles/{slugVin}/history — raw Apibara passthrough (snake_case, unmapped).
// Field names are the API's own — kept loose since the backend forwards the JSON as-is.
export interface ApibaraHistoryEntry {
    platform?: string;
    lot_number?: number | string;
    date?: string;
    price?: number;
    status?: string;
    [key: string]: unknown;
}

export interface ApibaraHistoryResult {
    ok?: boolean;
    data?: {
        vehicle?: { slug_vin?: string; vin?: string; platform?: string; lot_number?: string };
        history?: ApibaraHistoryEntry[];
    };
    meta?: { per_page?: number; next_cursor?: string | null; prev_cursor?: string | null };
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
