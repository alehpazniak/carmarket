# Task: make/model and year dropdowns on `/aukcje`, year dropdowns on the home page

> **How to use this file.** It was written outside your session, against commit `7dcdef2` (2026-10-24).
> Before relying on any statement about the code below, check it against the current repo; if the code has
> moved on, trust the code and tell me what differs. Work one stage at a time, one commit per stage. After a
> stage, tick it in **Progress** at the bottom and add one line on what was done.

Scope: `avtovo-frontend` and `auction-import-service`. No api-gateway or search-service changes.

## Decisions already made (don't revisit)

- `/aukcje` make/model dropdowns list **only the makes/models that currently have live lots in our database** (facets), not the full otomoto catalog. Every choice must give results.
  Reason: the catalog uses otomoto names ("Seria 3", "Klasa C", "F150"), Apibara stores US names in capitals ("TOYOTA", "CX-5", "F-150"), and on the free plan the sync only pulls the few `apibara.targets`.
- The home page catalog (`src/constants/vehicleMakes.ts`, via `makesFor`/`modelsFor` in `src/constants/cars.ts`) is used only to **display** nicer names for the facet values.
- Year lists run **from next year down to the oldest year**, newest first. Next year is included because US/EU model years start mid-year (2027 models are on sale in October 2026). The list is computed from the current date on every render, so on 1 January it gains a year by itself: no cron, no backend job, no config change.
- The home page "Rok od"/"Rok do" become the same kind of dropdown.

## Stage 1. Shared year dropdowns (frontend only)

1. Add `src/utils/years.ts`:
   ```ts
   /** Selectable years, newest first: next calendar year down to `oldest`. Computed from `today` on every call,
    *  so on 1 January the list gains a year by itself. Uses the browser's local time (Europe/Warsaw for our users). */
   export function yearOptions(oldest: number, today: Date = new Date()): number[]
   export const OLDEST_YEAR_LISTINGS = 1900; // home page: same lower bound as today's number inputs
   export const OLDEST_YEAR_AUCTIONS = 1990; // /aukcje: US auction imports
   ```
   Keep it a pure function (no module-level cached array: a tab left open over New Year must pick up the new year on the next render).
2. Home page (`src/pages/Home.tsx`): replace the "Rok od"/"Rok do" `<input type="number">` with `<select>`s styled like the other selects on that page. Empty first option ("Rok od" / "Rok do") means "any". The state stays strings, and the search request and search-service stay unchanged.
3. `/aukcje` (`src/pages/AuctionActions.tsx`): the same for its "Rok od"/"Rok do" inputs, styled like that form's fields, with `OLDEST_YEAR_AUCTIONS`.
4. On both pages "Rok do" offers only years ≥ the selected "Rok od", and "Rok od" only years ≤ the selected "Rok do", so an inverted range can't be picked.
5. `npm run build && npm run lint`. The frontend has no test runner; don't add one for this.

## Stage 2. Facets endpoint and exact make/model filter (auction-import-service)

1. `GET /api/auctions/lots/facets` (public GET, already covered by `/api/auctions/lots/**` in the service's `SecurityConfig` and the gateway's `JwtAuthenticationFilter`). The literal mapping must win over `/lots/{id}`; add a test for that.
   Response, as records (not entities):
   ```json
   { "makes": [ { "make": "TOYOTA", "count": 12,
                  "models": [ { "model": "RAV4", "count": 7 }, { "model": "CAMRY", "count": 5 } ] } ] }
   ```
   Values are `UPPER(TRIM(...))` of the stored make/model, so "Toyota" and "TOYOTA" lots group together. Sort makes and models alphabetically. One grouped query (`make, model, count`), assembled in Java.
2. **Same visibility as the listing.** Facets must count exactly the lots `searchLiveLots` can return (today `status = 'LIVE'`; stage 4 of `docs/tasks/auction-lots-sync.md` adds the auction-date rule). Put the condition in one place, e.g. a `static final String` JPQL fragment used by both `@Query`s, so the two can't drift.
3. `GET /api/auctions/lots`: `make` and `model` become **case-insensitive exact matches** (`UPPER(TRIM(a.make)) = :make`, same for model), replacing the model `LIKE '%…%'`: with a model dropdown, "3" must not match "CX-30" or "323". Normalize the parameters in Java (`trim().toUpperCase(Locale.ROOT)`, blank → null) and never wrap a parameter in a function in JPQL: a null parameter inside `UPPER()`/`CONCAT()` is mis-typed as `bytea` by the Postgres driver (see the comment in `AuctionLotController`). Check that nothing else calls `/lots` with a partial model.
4. Tests:
   - Testcontainers repository test (like `SyncRunRepositoryTest`): facets group case-insensitively, exclude non-LIVE lots, and for every make/model the facet count equals the `totalElements` of `/lots` with that make/model.
   - Controller test: `/lots/facets` doesn't hit `/lots/{id}`, and it's reachable without a login.
   - `FrontendJsonContractTest`: add the facets response.
   - `mvn -pl auction-import-service clean verify`.
5. In `docs/tasks/auction-lots-sync.md` (if those stages are still unchecked), add `/lots/facets` to stage 4 (shared visibility rule) and stage 5 (cached and evicted together with `/lots` and `/lots/meta`).

## Stage 3. Make/model dropdowns on `/aukcje` (frontend)

1. `src/api/auctions.ts`: `getLotFacets()`, plus the types in `src/types/auctions.ts`.
2. `AuctionActions.tsx`: replace the "Marka"/"Model" text inputs with `<select>`s, laid out like the home page's make/model pair:
   - Make: "Wszystkie marki" + one option per facet, labelled `Toyota (12)`. The option value is the facet value ("TOYOTA").
   - Model: disabled with "Najpierw wybierz markę" until a make is chosen; then "Wszystkie modele" + that make's models with counts. Changing the make resets the model.
   - Load facets once on mount, in parallel with the first lot page. If the request fails, log it and show the selects with only their empty option; the list still works. With no lots at all, disable both selects.
3. Display names: add a helper (e.g. `src/utils/auctionNames.ts`) that maps a facet value to the catalog spelling when the normalized forms match (uppercase, letters and digits only): `TOYOTA` → `Toyota`, `MERCEDES-BENZ` → `Mercedes-Benz`, `F-150` → `F150`, `4RUNNER` → `4-Runner`, `BMW` stays `BMW`. Use `makesFor('PASSENGER')` for makes and `modelsFor('PASSENGER', catalogMake)` for models; ignore the catalog's "Inny". No match → show the facet value as is. Don't add an alias table ("Seria 3" ↔ "3 SERIES"); if real data shows names that should merge, tell me instead.
4. The lot cards keep showing the raw make/model; only the dropdowns use display names.
5. `npm run build && npm run lint`.

## Out of scope

- `/dealerzy` and the dealer pages.
- search-service and the home page's make/model lists (only its year inputs change).
- api-gateway.
- `AuctionLotDetail.tsx` and its calculators.
- `../avtovo` (list at the end what should be ported there).

## When done

List what I should check by hand in the browser (`npm run dev`) on desktop and mobile widths, including how to check the facets with an empty `auction_lots` table and with a few lots.

## Progress

- [x] Stage 1. Shared year dropdowns
  - `src/utils/years.ts` (`yearOptions`, pure, next year → oldest); "Rok od"/"Rok do" are `<select>`s on Home (1900, string state, request unchanged) and `/aukcje` (1990), each list limited by the other so an inverted range can't be picked.
- [ ] Stage 2. Facets endpoint and exact make/model filter
- [ ] Stage 3. Make/model dropdowns on `/aukcje`
