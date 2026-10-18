/** Public dealer pages (/dealer/:slug), user-service's DealerPageDtos. */

/** day: 1 = poniedziałek … 7 = niedziela (ISO); open/close as HH:MM. Days without an entry are closed. */
export interface DayHours {
    day: number;
    open: string;
    close: string;
}

export interface TeamMember {
    name: string;
    role: string | null;
}

interface DealerPageContent {
    slug: string;
    companyName: string;
    headline: string | null;
    description: string | null;
    city: string | null;
    street: string | null;
    postalCode: string | null;
    /** WGS84; both set or both null (null = not on the dealer map). */
    latitude: number | null;
    longitude: number | null;
    website: string | null;
    facebookUrl: string | null;
    instagramUrl: string | null;
    youtubeUrl: string | null;
    logoUrl: string | null;
    bannerUrl: string | null;
    galleryUrls: string[];
    openingHours: DayHours[];
    services: string[];
    team: TeamMember[];
}

/** GET /api/users/dealers/{slug}: no phone, only whether there is one ("Wyświetl numer"). */
export interface DealerPageView extends DealerPageContent {
    userId: string;
    hasPhone: boolean;
    memberSince: string | null;
}

/** GET/PUT /api/users/me/dealer-page: the owner's own page. */
export interface DealerPageOwner extends DealerPageContent {
    phone: string | null;
    published: boolean;
    updatedAt: string | null;
}

export type DealerPageRequest = Omit<DealerPageOwner, 'updatedAt'>;

/** GET /api/users/dealers/by-user/{userId}: the dealer box on a listing page. */
export interface DealerCard {
    slug: string;
    companyName: string;
    logoUrl: string | null;
    city: string | null;
}

/** GET /api/users/dealers/nearby: one marker on the dealer map (/dealerzy), nearest first. */
export interface DealerMapItem {
    slug: string;
    companyName: string;
    logoUrl: string | null;
    city: string | null;
    street: string | null;
    postalCode: string | null;
    latitude: number;
    longitude: number;
    distanceKm: number;
}
