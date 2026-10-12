import type { AuctionLot, ApibaraVehicleDetail } from '../types/auctions';
import type { CreateImportOrderRequest, LotSource } from '../types/importOrders';

type ApibaraVehicleData = NonNullable<ApibaraVehicleDetail['data']>;

/** What the create form is opened with from an auction page (router state). */
export type ImportOrderPrefill =
    | { kind: 'apibara'; vehicle: ApibaraVehicleData }
    | { kind: 'lot'; lot: AuctionLot };

export function sourceFromPlatform(platform: string | null | undefined): LotSource {
    const p = (platform ?? '').toLowerCase();
    if (p.includes('copart')) return 'COPART';
    if (p.includes('iaai') || p.includes('insurance')) return 'IAAI';
    return 'OTHER';
}

/** The suggested purchase price: only a hint, the dealer confirms the real amount from the auction invoice. */
export interface PrefillResult {
    fields: CreateImportOrderRequest;
    suggestedPriceUsd: number | null;
}

/** Apibara vehicle (GET /api/auctions/apibara/vehicles/{id}) → order fields; the whole JSON becomes the snapshot. */
export function fromApibaraVehicle(v: ApibaraVehicleData): PrefillResult {
    return {
        fields: {
            source: sourceFromPlatform(v.platform),
            lotNumber: v.lot_number ?? null,
            vin: v.vin ?? null,
            make: v.make ?? null,
            model: v.model ?? null,
            year: v.year ?? null,
            auctionLocation: v.location?.display ?? null,
            titleType: v.sale_document?.name ?? null,
            titleExportOk: v.sale_document?.export ?? null,
            titleRegistrationOk: v.sale_document?.registration ?? null,
            saleDate: v.auction?.auction_at ? v.auction.auction_at.slice(0, 10) : null,
            auctionSnapshot: v as unknown as Record<string, unknown>,
        },
        suggestedPriceUsd: v.pricing?.last_sold_price_usd ?? null,
    };
}

/** A lot from our synced table (GET /api/auctions/lots/{id}); no Apibara request needed. */
export function fromAuctionLot(lot: AuctionLot): PrefillResult {
    return {
        fields: {
            source: lot.source === 'COPART' || lot.source === 'IAAI' ? lot.source : 'OTHER',
            lotNumber: lot.lotNumber ?? null,
            vin: lot.vin ?? null,
            make: lot.make ?? null,
            model: lot.model ?? null,
            year: lot.year ?? null,
            auctionLocation: lot.auctionLocation ?? null,
            saleDate: (lot.saleDate ?? lot.auctionDate)?.slice(0, 10) ?? null,
            auctionSnapshot: lot as unknown as Record<string, unknown>,
        },
        suggestedPriceUsd: lot.status === 'SOLD' ? lot.auctionPrice ?? null : null,
    };
}

export interface AuctionPhoto {
    thumb: string;
    large: string;
}

/** Photo URLs in a stored snapshot: Apibara media.items / media.thumbs, or a synced lot's imageUrls. */
export function auctionSnapshotPhotos(snapshot: Record<string, unknown> | null | undefined): AuctionPhoto[] {
    if (!snapshot) return [];
    const media = snapshot.media as { items?: { thumb?: string; large?: string; url?: string; type?: string }[]; thumbs?: string[] } | undefined;
    const fromItems = (media?.items ?? [])
        .filter(i => !i.type || i.type === 'image' || i.type === 'photo')
        .map(i => ({ thumb: i.thumb ?? i.large ?? i.url ?? '', large: i.large ?? i.url ?? i.thumb ?? '' }))
        .filter(p => p.large);
    if (fromItems.length) return fromItems;
    if (media?.thumbs?.length) return media.thumbs.map(t => ({ thumb: t, large: t }));
    const urls = snapshot.imageUrls;
    return Array.isArray(urls) ? urls.filter((u): u is string => typeof u === 'string').map(u => ({ thumb: u, large: u })) : [];
}
