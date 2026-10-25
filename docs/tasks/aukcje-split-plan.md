# Aukcje / Dealerzy split — plan for Claude Code

## Context

- Two codebases:
  - `carmarket` — Spring Boot microservices + React frontend (`avtovo-frontend/`). It becomes **Dealerzy**.
  - `avtovo` — sibling directory `../avtovo`: one Spring Boot app, React frontend embedded in the jar (`frontend/` → `classpath:/static`), one Postgres, Flyway + `ddl-auto: validate`. It becomes **Aukcje**. It is not under git yet.
- Read `carmarket/CLAUDE.md` before anything else. It documents auction-import-service, import-order-service, the gateway rate limiters and the avtovo monolith.

## Agreed decisions

### Aukcje (avtovo)

- One process, one jar, one Postgres. No Kafka, no Redis, no Eureka.
- Owns everything about US auctions: lot search, lot pages, Apibara live lookups, calculators (import cost, max bid, W8 transport), import orders (zlecenia), stock (cars bought for resale), inquiries.
- One dealer: the owner. No DEALER role, no dealer upgrade, no dealer slugs. Whatever a dealer did under carmarket's `/panel-dealera/*` lives in the admin panel. Keep `dealer_id` on import orders anyway (possible multi-dealer product later).
- Visitors never log in. Only e-mails from `ADMIN_EMAILS` may sign in (Google OAuth). The public UI has no login button.
- The Apibara quota is protected by anonymous rate limits only (per IP + a global rolling budget), configurable for the Basic plan (30,000 requests/month).
- Notifications in-process: after-commit event listener → notifications table → scheduled sender (Spring Mail), same batching semantics as carmarket's notification-service.
- Admin audit: written to a table in the same transaction.
- **Stock = import order with `purpose = STOCK`** (no client). Where the car is = the existing status timeline. Payment flags are separate: cło + VAT paid (actual amounts from the customs document), akcyza paid (actual amount), badanie techniczne, tłumaczenia → derived "gotowy do rejestracji". Sales state: available / reserved / sold. Selling a stock car attaches a buyer and enables the client page `/zlecenie/<code>`.
- **Stock price has three stages**, tied to the payment flags:
  1. in transit — price without cło, VAT and akcyza;
  2. cło + VAT paid — price including cło and VAT, without akcyza;
  3. akcyza paid — turnkey price.

  On each flag change the system proposes the next price (current price + actual amounts paid + optional default margin from settings) in a confirmation dialog; the admin accepts or types another amount. The price never changes silently. Every change goes to a price history (stage, amount, AUTO/MANUAL, actor, timestamp), admin only.

  Next to the public price, always show what is still to be paid: stage 1 "Do zapłaty dodatkowo: cło, VAT i akcyza (ok. X zł)", stage 2 "Do zapłaty dodatkowo: akcyza (ok. X zł)", stage 3 "Cena pod klucz". X comes from the calculator. Cars in transit show an expected availability date.
- **Inquiry cart**: anonymous, kept in the browser (localStorage, 24 h TTL), holds auction lots and stock cars; ended auctions are flagged, not dropped. One submit form: name (optional), phone or e-mail (at least one), comment (optional), RODO consent (required, link to the privacy policy); honeypot field + per-IP limit, no captcha. The server stores a snapshot of every car (link, source, lot number, VIN, auction date, current bid, photo URL, title). The admin gets an e-mail immediately (not batched); the client gets a confirmation if they left an e-mail. Admin "Zapytania": NEW → CONTACTED → CONVERTED / REJECTED, one click per car → prefilled import order.
- **Company page**: "Dane firmy" in the admin panel (name, NIP, address, phone, e-mail, opening hours, map location, gallery, about text) → public "O nas" / "Kontakt".

### Dealerzy (carmarket), later

- After Aukcje runs: remove auction-import-service, import-order-service, their gateway routes, frontend pages `/aukcje*`, `/panel-dealera/zlecenia*`, `/zlecenie/*`, the `import-order.client-update` consumer and the related admin tiles.
- Keep: home page listing search, private sellers, chat, Przelewy24 promotions, dealer pages, dealer map, admin panel.
- Dealer verification: NIP required, checked against the Biała lista VAT API (100 queries/day, ≤ 30 entities per query), then manual admin approval. Until approved, the dealer page is not public and not on the map.
- Admin-added catalog dealers (entered by hand from CEIDG/KRS/websites, never copied from Google Places; only `place_id` may be stored) with a "To moja firma" claim flow (NIP must match + admin approval) and a removal option.
- Aukcje publishes its stock to Dealerzy as the owner's dealer listings (upsert by external id, service key).

## Stages

1. Baseline + auction sync + visitors without login (below)
2. Import orders in avtovo (port of import-order-service, admin only)
3. Stock + price stages
4. Inquiry cart + "Zapytania"
5. "Dane firmy" + admin panel assembly
6. carmarket cleanup (Dealerzy)
7. Dealerzy: dealer verification, catalog + claim, stock syndication from Aukcje

## Stage 1 — task

Work in `../avtovo`; `../carmarket` is read-only reference. Do not change carmarket in this stage.

### Step 0 — safety

- If `avtovo` is not a git repository: `git init`, a `.gitignore` (target/, node_modules/, dist/, .env, IDE files), and commit the current state as "Baseline before Aukcje split". Never commit secrets.
- Build and run the tests. Report the result. Don't start changes on a red build without telling me.

### Step 1 — divergence report. STOP after this step and wait for my OK.

Compare avtovo's auction code (backend `com.carmarket.avtovo.auction` and the frontend auction pages/components) with carmarket's `auction-import-service`, the auction parts of `avtovo-frontend`, and `api-gateway`'s `ApibaraRateLimiter` / `ClientIpResolver`. Output a table: feature / carmarket / avtovo / action. Check at least:

- W8 shipping: towing rates (carmarket V5), `W8ShippingConfig`, `W8LocationKey`, `W8ShippingQuoteService`, the "Kalkulator transportu W8" card
- duty by VIN (`CustomsDutyResolver`, the `Clock` bean), `ExciseRateResolver`
- `MaxBidCalculator` including the costs after the EU border
- `LiveVehicleLookup`, `LotLookupService`, `LotLookupBar`
- Apibara failure reasons (429 `QUOTA_EXHAUSTED` / `RATE_LIMITED`, 502 `UNAVAILABLE`, 503) and `apibaraErrorMessage`
- sync runs and quota tracking (carmarket V4, V7, V11), the feed model (V8, V9), V10
- admin status and manual sync endpoints, analytics

Also list what avtovo has and carmarket doesn't (e.g. Copart + IAAI sync, lots unique per `(source, lot_number)`). Those must be kept.

Recommend one approach: port feature by feature, or replace the auction package with a fresh copy and re-apply the avtovo-only changes.

### Step 2 — port (after my OK)

- Bring avtovo's auction behaviour to parity with carmarket, keeping the avtovo-only features.
- No gateway here: no `GatewaySignatureFilter`, no `X-User-Id`; the user id comes from `Authentication#getName()`.
- Schema changes go into new Flyway migrations in avtovo's own numbering. The W8 towing rates come in as a new migration with the same `price_list` value. Never edit an applied migration.

### Step 3 — visitors without login

- Only e-mails from `ADMIN_EMAILS` (comma-separated env var) can sign in. Any other Google account is rejected in the OAuth success handler before a user row is created; redirect to the frontend with `?error=not_allowed`. Existing non-admin rows stay but can't log in.
- Remove the public "Zaloguj się" button. The admin signs in at `/admin` (redirect to Google when not authenticated).
- Everything visitors could do stays available anonymously: lot search, lot pages, live Apibara lookups, max-bid and W8 calculators. Endpoints that needed a logged-in user but are used by these pages (e.g. the W8 quote, the auction image proxy) become public and rate-limited; the image proxy must only fetch from known auction image hosts. Apibara usage and every admin endpoint stay ADMIN only.

### Step 4 — rate limits in memory

- In-memory buckets (Bucket4j or a small own implementation). Single instance assumed; note in CLAUDE.md that two instances would need a shared store.
- Apibara live lookups: per IP (`APIBARA_ANON_PER_IP_PER_SECOND` = 1, `APIBARA_ANON_PER_IP_BURST` = 5), then a global rolling 30-day budget (`APIBARA_ANON_GLOBAL_PER_MONTH`), charged only when the per-IP bucket allows — same semantics as carmarket's `ApibaraRateLimiter`. 429 responses keep the body shapes the frontend already handles. Default the global budget for the free plan and document the value to use on Basic.
- Client IP: same semantics as carmarket's `ClientIpResolver`, with a `TRUSTED_PROXIES` count (0 locally = remote address, client-sent `X-Forwarded-For` ignored).

### Step 5 — done means

- `mvn verify` green (Testcontainers), `npm run build` and `npm run lint` green.
- New tests: admin-only login (allowed vs rejected e-mail), the anonymous Apibara limiter (per IP, global budget), and any ported calculator behaviour not covered yet.
- avtovo's CLAUDE.md (create it if missing) says avtovo is now the only home of the auction code and carmarket's auction-import-service is frozen until removal.
- Small logical commits. Finish with a summary of what changed and anything skipped.
