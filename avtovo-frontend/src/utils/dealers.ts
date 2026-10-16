import type { DealerPageOwner, DealerPageRequest } from '../types/dealers';

/** 1 ogłoszenie, 2–4 ogłoszenia, 5+ ogłoszeń (12–14 too). */
export function offersLabel(n: number): string {
    if (n === 1) return '1 ogłoszenie';
    const last = n % 10, lastTwo = n % 100;
    return `${n} ${last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? 'ogłoszenia' : 'ogłoszeń'}`;
}

/** "Auto Łódź-Kowalski" → "auto-lodz-kowalski" (dealer page address). */
export function slugify(text: string): string {
    return text.toLowerCase()
        .replace(/ł/g, 'l')
        .normalize('NFD').replace(/[̀-ͯ]/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .slice(0, 40)
        .replace(/-+$/g, '');
}

/** The owner's page as the PUT body (without the server-owned updatedAt). */
export function toDealerPageRequest(page: DealerPageOwner): DealerPageRequest {
    const copy: Partial<DealerPageOwner> = { ...page };
    delete copy.updatedAt;
    return copy as DealerPageRequest;
}
