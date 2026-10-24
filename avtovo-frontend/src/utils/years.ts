/** Selectable years, newest first: next calendar year down to `oldest`. Computed from `today` on every call,
 *  so on 1 January the list gains a year by itself. Uses the browser's local time (Europe/Warsaw for our users). */
export function yearOptions(oldest: number, today: Date = new Date()): number[] {
    const years: number[] = [];
    for (let year = today.getFullYear() + 1; year >= oldest; year--) years.push(year);
    return years;
}

export const OLDEST_YEAR_LISTINGS = 1900; // home page: same lower bound as today's number inputs
export const OLDEST_YEAR_AUCTIONS = 1990; // /aukcje: US auction imports
