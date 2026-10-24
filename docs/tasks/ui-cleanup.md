# Task: UI cleanup — home page and the "Aukcje" tab

> **How to use this file.** It was written outside your session, against commit `062287b` (2026-10-21).
> Before relying on any statement about the code below, check it against the current repo; if the code has
> moved on, trust the code and tell me what differs. Work one stage at a time, one commit per stage. After a
> stage, tick it in **Progress** at the bottom and add one line on what was done.

Scope: `avtovo-frontend` only. No backend, gateway or API changes.

## Stage 1. Home page (`src/pages/Home.tsx`)

1. Hide the subtitle "Tysiące ogłoszeń motoryzacyjnych w jednym miejscu" and the category tabs
   (Osobowe, Elektryczne, Ciężarowe, Budowlane, Dostawcze, Motocykle, Przyczepy, Rolnicze).
   **Frontend only and reversible: they will come back later.**
   - Add `src/config/features.ts` with `export const FEATURES = { homeCategoryTabs: false } as const;` and a comment saying what the flag controls.
   - Render the subtitle and the tabs only when the flag is on. Keep `CATEGORY_TABS`, the `tab` state, `handleTabChange` and the per-category make/model logic intact.
   - With the flag off, the page behaves exactly as it does now on the default tab: search sends `category=PASSENGER`, and the make/model lists are the passenger-car ones. Don't change search-service or the search API.
   - Keep the hero's vertical spacing balanced after the removal (`h1` has `mb-4`, the subtitle `mb-8`, the tabs `mb-6`).
2. Remove the "Zaloguj się przez Google" button under the search form (the `!isAuthenticated` block). Login stays in the Navbar ("Zaloguj się", visible at every width). Drop `loginWithGoogle` / `isAuthenticated` from `useAuth()` in Home if they become unused.
3. Verify: `npm run build && npm run lint`. Flipping the flag to `true` must bring the subtitle and tabs back exactly as before.

## Stage 2. "Aukcje" tab (`src/pages/AuctionActions.tsx`): only the lot list

`/aukcje` should show only the list of auction lots from our database (`GET /api/auctions/lots`, filled by the background Apibara sync).
The calculators belong to each lot's page `/aukcje/lot/:id` (`AuctionLotDetail.tsx`), which already has the import-cost and max-bid calculators with W8 transport. Don't change that page.

1. Stop rendering `<W8TransportCalculator />` and `<ApibaraQuickTools />` (shipping estimate by VIN/lot number, live Apibara search, VIN/URL lookup) on `/aukcje`.
   Don't lose the code: move `ApibaraQuickTools` (and the `Row` helper if only it uses it) to `src/components/auctions/ApibaraQuickTools.tsx`, unrendered, with a comment that it's kept for later. Leave `W8TransportCalculator.tsx` as is.
   Keep the routes `/aukcje/lot/:id` and `/aukcje/pojazd/:identifier`.
2. Keep the header (including the dealer's "Zlecenia importu" link), the filters, the grid, pagination and the loading skeleton.
   Update the subtitle: it currently promises import costs and transport on this page. Polish copy, e.g. "Auta z aukcji Copart i IAAI w USA. Koszt importu policzysz na stronie auta."
3. Empty state: the list stays empty until the sync has run with `apibara.targets` configured. Replace the single "Brak aukcji spełniających kryteria" with two cases:
   - no filters → "Brak aktualnych aukcji. Lista odświeża się automatycznie."
   - with filters → the current text.
4. Remove imports that became unused. `npm run build && npm run lint` must pass.
5. `docs/tasks/auction-lots-sync.md`, stage 4 (if still unchecked), says to leave the "Szybkie sprawdzenie w Apibara" block alone. That block no longer renders on `/aukcje`, so edit that bullet accordingly, so the sync task doesn't bring it back.

## Out of scope

- `/dealerzy` and the dealer pages.
- Backend, api-gateway, search-service.
- The calculators on `AuctionLotDetail.tsx`.

## When done

List what I should check by hand in the browser (`npm run dev`), for desktop and mobile widths.

## Progress

- [x] Stage 1. Home page
  - `FEATURES.homeCategoryTabs` (`src/config/features.ts`, off) hides the subtitle and tabs (h1 gets `mb-8` while off); Google login button removed; tab/category logic untouched, search still sends `category=PASSENGER`.
- [x] Stage 2. "Aukcje": only the lot list
  - `/aukcje` renders header, filters, grid, pagination only; `ApibaraQuickTools` + `Row` moved unrendered to `src/components/auctions/ApibaraQuickTools.tsx`; new subtitle; empty state depends on the filters of the last loaded list; `auction-lots-sync.md` stage 4 bullet updated.
