# Task: Apibara feed → our database (all cars, SUVs, vans, motorcycles; upcoming and ended lots)

> **How to use this file.** It was written outside your session, against commit `7dcdef2` (2026-10-24).
> Before relying on any statement about the code below, check it against the current repo; if the code has
> moved on, trust the code and tell me what differs. Work one stage at a time, one commit per stage. After a
> stage, tick it in **Progress** at the bottom and add one line on what was done.

Scope: `auction-import-service` only (plus `CLAUDE.md` and the task files named below). **No new microservice.**

This task **replaces Stage 3 of `docs/tasks/auction-lots-sync.md`** ("Incremental sync within a budget"): mark that stage there as
"superseded by docs/tasks/auction-feed-sync.md". Its budget rules are reused below. Stages 4 and 5 there stay.

## Goal

A scheduled sync stores Copart and IAAI lots in `auction_lots`, without a make/model list:

- **Included vehicle types:** passenger cars (sedans, hatchbacks, coupes, wagons), SUVs/crossovers, vans/minivans, motorcycles.
- **Excluded:** trucks and special equipment, i.e. heavy/medium-duty trucks, buses, trailers, RVs, industrial, construction and agricultural equipment, boats, jet skis, snowmobiles.
- **Undecided until Stage 0:** pickups (F-150, RAM 1500) and ATVs/quads. Propose a split, then I decide.
- **Upcoming/live lots** are stored and shown on `/aukcje`, as now.
- **Ended lots are stored too, not deleted:** final result (sold / not sold), final price and sale date. They don't appear on `/aukcje` (the listing and `/lots/facets` show only live upcoming lots). They feed `/lots/{id}/comparables` (`findSoldComparables`) and analytics.

## The budget is the hard limit: read this first

Apibara reports about **30,000 records added or refreshed per day** across Copart/IAAI, with `per_page` capped at 20/30/40 by plan.
The free plan is **100 requests a month**, half of which the gateway keeps for anonymous live lookups.
So full coverage is impossible on the free plan: even excluding trucks, a full feed needs hundreds of requests a day.

The sync must therefore:
- never exceed its budget (the rules from `auction-lots-sync.md` Stage 3: `monthly-budget`, even pacing, `keep-remaining`, 429/`Retry-After`, advisory lock);
- spend the budget on the most useful records first;
- **report how far behind it is**, so I can see what a bigger plan would buy;
- run unchanged on a paid plan by only raising `monthly-budget`.

## Stage 0. Verify the API and propose the type split (no code; stop for my OK)

Check `https://apibara.tech/openapi/v1.json`, the docs (`/en/products/vehicle-auction-data-api/docs`, `/en/vehicle-auction-status-api`, `/en/vehicle-auction-incremental-sync-api`) and the fixtures `src/test/java/com/carmarket/auction/apibara.tech_*.json`, `src/test/resources/apibara/*.json`.
Use at most **2 real requests** (e.g. `GET /vehicles/filters`, if it isn't documented as free), and say what they cost.
Answer, in a **Findings** section appended to this file:

1. Which field holds the vehicle type and/or body style, and all its values (from `/vehicles/filters`). Can `GET /vehicles` filter by it server-side? Does it accept several values in one request, or a negative filter?
2. Your proposed include/exclude split of those exact values, with pickups and ATVs called out.
3. How ended lots look: state values, sold vs not sold, final price field, sale date field. Does an ended lot's `updated_at` change when the result is posted (so `updated_within_minutes` catches it)? Does a live bid change `updated_at` (that would multiply the volume)?
4. Sorting: can `/vehicles` sort by `updated_at` ascending, or by auction date? Is there an auction-date range filter? Does the response include a total count?
5. A volume estimate: records per day for the included types, and requests per month for full coverage at your plan's `per_page`. Use the response's total count if there is one; otherwise say what you based it on.
6. Whether `platform` and `auction_type` can be combined, and whether the `make`/`model` filters are still needed for anything.

**Then stop and wait for my OK on the split before Stage 1.**

## Stage 1. Data model and mapping

Migration: the next free `V<N>__...` (V1–V7 exist; don't edit them; `ddl-auto: validate`):

- `auction_lots`:
  - `vehicle_type VARCHAR(50)` and, if Apibara has it, `body_style VARCHAR(50)`, both as Apibara sends them, plus an index on `vehicle_type`;
  - `sold_price NUMERIC(12,2)`;
  - `last_seen_at TIMESTAMPTZ`;
  - `ended_at TIMESTAMPTZ`.
  `sale_date` already exists.
- `sync_streams` (one row per stream, see Stage 2): `stream_key` PK, `watermark TIMESTAMPTZ`, `last_full_at`, `last_run_at`, `backlog BOOLEAN`, `backlog_estimate INT`.
- `sync_runs`: `mode`, `stop_reason`, `window_minutes`, `streams_done`, `streams_with_backlog` (skip any column a previous task already added).

Mapping (`ApibaraAuctionParser.toLot`, `mapStatus`):

- Store `vehicle_type`/`body_style`. A record of an excluded type is skipped and counted (`skipped_type`), in case the API filter isn't exhaustive.
- Status:
  - upcoming/open/live → `LIVE`;
  - ended + sold → `SOLD` (with `sold_price`, `sale_date`, `ended_at`);
  - ended + not sold → `UNSOLD`;
  - removed → `REMOVED`.
  Fix the current `timed` → `UNSOLD` mapping: `timed` is an auction mode, not a result.
- A status from the feed always wins over `EXPIRED` (which Stage 4 of `auction-lots-sync.md` sets as a fallback). Edit that stage's text so the expiry job only touches lots Apibara never reported as ended.
- `SOLD`/`UNSOLD` don't go back to `LIVE`. If Apibara relists a car under the same `(source, lot_number)`, log a WARN and take Apibara's state.
- `last_seen_at = now()` on every upsert, also when `content_hash` is unchanged. That's one cheap UPDATE per page, not per row.
- Remove the make/model `apibara.targets` config and `ParserTargetsConfig.Target` (`matchesYear` included) once Stage 2 no longer uses them.

Tests: mapping against the fixtures (vehicle type, every state, the sold price), and the excluded-type skip.

## Stage 2. The feed sync (superseded by docs/tasks/auction-feed-rollout.md)

**Streams.** One stream per `platform` (copart, iaai) × type filter value, or one per platform if the API takes all included types in one request. `stream_key` example: `copart:automobile`. Rotate the starting stream between runs (round-robin) so none starves.

**Per stream, per run:**

1. **First run (no watermark): BACKFILL.** Only upcoming/live lots (state filter), soonest auction first if the API can sort by auction date, limited to `backfill-upcoming-days` if there is a date filter. No history backfill of ended lots: ended lots accumulate from the moment we start syncing. Set the watermark to the start time of the backfill run once the backfill is drained.
2. **Afterwards: INCREMENTAL.** `updated_within_minutes` from the watermark minus `overlap-minutes`, no state filter, so new lots, changed lots and newly ended lots all come in.
   - **If the API sorts by `updated_at` ascending:** advance the watermark to the `updated_at` of the last saved record after every page, so a run stopped by the budget resumes exactly there.
   - **If it doesn't:** don't advance the watermark until the window is fully drained (no `next_cursor`). A run stopped mid-window leaves the watermark and sets `backlog = true`.
   - An `updated_within_minutes` above 525,600 isn't possible. If the watermark is older than a year, restart with BACKFILL and log a WARN.
3. **Budget:** exactly the rules of `auction-lots-sync.md` Stage 3, points 1, 4, 5 and 6 (allowance, 429, advisory lock, manual run obeys the budget).
   - The allowance is spent across streams by round-robin, one page at a time, so one stream can't eat the whole run.
   - When the allowance runs out, record `stop_reason = BUDGET` and, per stream, `backlog = true` and `backlog_estimate` (from the total count if available).

Config (`application.yml`, commented with the math for the free plan and for a paid one):

```yaml
apibara:
  platforms: [copart, iaai]
  per-page: 40
  vehicle-types:
    include: []        # Apibara's exact values, agreed in Stage 0
  sync:
    cron: "0 0 */6 * * *"
    zone: Europe/Warsaw
    monthly-budget: 30
    start-burst: 3
    keep-remaining: 15
    overlap-minutes: 30
    backfill-upcoming-days: 14
  retention:
    ended-lots-days: 365   # delete ended lots older than this (0 = keep forever)
    raw-data-days: 30      # set raw_data to NULL on ended lots older than this
```

`GET /api/auctions/admin/status`: add per stream `watermark`, `backlog`, `backlog_estimate`, `last_run_at`, plus a one-line coverage verdict ("Pokrycie pełne" / "Zaległości: ~N rekordów, budżet za mały").

Tests (unit with a mocked client, Testcontainers for the repository parts):
- BACKFILL then INCREMENTAL;
- watermark: advance per page with sorting, hold when interrupted without sorting;
- round-robin across streams;
- budget stop with backlog;
- 429;
- a newly ended lot becoming `SOLD` with its price;
- idempotent re-run of the same window (overlap) changing nothing.

Update `LotSyncSchedulerTest`, keep `FrontendJsonContractTest` green, and run `mvn -pl auction-import-service clean verify`.

## Stage 3. Retention and docs (superseded by docs/tasks/auction-feed-rollout.md)

- `LotRetentionJob` (daily, no API calls):
  - delete `SOLD`/`UNSOLD`/`EXPIRED`/`REMOVED` lots with `ended_at` (or `auction_date`) older than `ended-lots-days`, together with their images and `import_calculations`;
  - null `raw_data` on ended lots older than `raw-data-days`.
  Batch the deletes (e.g. 1,000 rows per transaction), publish nothing to Kafka, and audit as actor `system` only if the existing `AdminAuditPublisher` pattern fits.
- Check that `/lots`, `/lots/facets` and `/lots/meta` still show only live upcoming lots, and that `findSoldComparables` now gets real `SOLD` rows.
- Update `CLAUDE.md` (auction-import-service section: streams, watermark, budget, ended lots, retention, config) and remove what describes `apibara.targets`. At the end, list what of this should be ported to `../avtovo`.

## Out of scope

- Frontend changes. Ended lots will matter for the lot page later; just tell me what you'd change there.
- api-gateway and `ApibaraRateLimiter`.
- The calculators and W8.

## Progress

- [x] Stage 0. API findings and type split: see **Findings** below; 2 real requests (`/vehicles/filters` and one `/vehicles` count). **Waiting for your OK on the split (F2) and the Stage 2 changes (F7).**
- [x] Stage 1. Data model and mapping
  - Split approved (pickups in, ATVs out); `TRUCK` sampled (1 request: IAAI commercial trucks) → excluded. **V8** (`auction_lots.vehicle_type/body_style/sold_price/last_seen_at/ended_at`, `sync_streams`, `sync_runs.mode/stop_reason/window_minutes/streams_done/streams_with_backlog`), `SyncStream` entity; `apibara.vehicle-types.include`; `toLot` maps source from `platform` (IAAI was stored as COPART) and status from `outcome_status`/`last_sold_status`/`lot_removed` (Sold on Approval / no result → EXPIRED); a final result survives stale records, a relist (later auction) takes the feed's state with a WARN; `last_seen_at` via one UPDATE per page. `apibara.targets` stays until Stage 2.
- [ ] Stage 2. Feed sync — superseded by docs/tasks/auction-feed-rollout.md
- [ ] Stage 3. Retention and docs — superseded by docs/tasks/auction-feed-rollout.md

## Findings (Stage 0, 2026-10-24)

Sources: `https://apibara.tech/openapi/v1.json` (OpenAPI 3.1), the three docs pages, `/en/pricing`, the fixtures, and **2 real requests** with our key:

| # | Request | Cost |
|---|---|---|
| 1 | `GET /vehicles/filters` | not documented as free; counted as 1 (after it: `X-Subscription-Used: 22`, nothing earlier to compare with) |
| 2 | `GET /vehicles?auction_type=0&type[]=SEDAN&type[]=AUTOMOBILE&type[]=COUPE&type[]=SUV&type[]=VAN&type[]=MOTORCYCLE&updated_within_minutes=1440&per_page=1&include_total=true` | 1 (`Used` 22 → 23, `Remaining: 77`) |

Pricing counts **per call, not per record**. Our key: `X-Per-Page-Limit: 20`, not 40 (`application.yml`'s `per-page: 40` is capped by the client anyway); limit 100/month.

### F1. Vehicle type and body style

- **`type`** (top-level on `Vehicle`) is filterable server-side with `type`, **several values OR-ed** in one request (`type[]=A&type[]=B`). The spec has **no negative filter**, so excluding means listing every included value. All 30 values from `/vehicles/filters` (`data.types`):
  `SEDAN, AUTOMOBILE, COUPE, SUV, PICKUP, VAN, RECREATIONAL VEHICLE (RV), ATV, TRAILERS, MEDIUM DUTY/BOX TRUCKS, BOAT, JET SKI, MOTORCYCLE, HEAVY DUTY TRUCKS, INDUSTRIAL EQUIPMENT, INSPECTION, SNOWMOBILE, DIRT BIKE, CONSTRUCTION EQUIPMENT, TRAILER, TRAVEL TRAILER, TRUCK, MOTOR HOME, FARM EQUIPMENT, HEAVY EQUIPMENT, OTHER, BUS, PERSONAL WATERCRAFT, AGRICULTURE AND FARM EQUIPMENT, EMERGENCY EQUIPMENT`.
  The vocabulary is mixed: coarse classes (`AUTOMOBILE`, `TRUCK`) sit next to body-like ones (`SEDAN`, `SUV`, `PICKUP`). The fixture Mazda CX-5 is `AUTOMOBILE`; the live sample SUV is `SUV`.
- **`vehicle_specs.body_style`** is filterable with `body_style` (alias `body_type`), several values OR-ed, case-insensitive **exact** match. There is **no value list**: it's free text (Copart's "Body Style", IAAI's "VehicleClass"). Seen so far: `SUV/Crossover`, `Sport utility vehicle`, `Suv`. That makes it unusable as the split key; store it for display and analytics only.
- **`type` can be null.** Fixture `5UXTR9C54KLD98072` (BMW X3) has `type: null`, `body_style: null`, and a `type[]` filter never returns such records. Either accept that loss, or add a separate low-priority pass without a type filter (skipping excluded types in the mapping) once the budget allows.

### F2. Proposed split (exact `type` values)

| Decision | Values |
|---|---|
| **Include** | `SEDAN`, `AUTOMOBILE`, `COUPE`, `SUV`, `VAN`, `MOTORCYCLE` |
| **Pickups: your call, I recommend include** | `PICKUP` (F-150, RAM 1500, Tacoma: a large share of US salvage and a common import to Poland; the calculators handle them like cars) |
| **`TRUCK`: unclear, decide after one sample** | It's separate from `MEDIUM DUTY/BOX TRUCKS` and `HEAVY DUTY TRUCKS`, so it's probably light trucks/pickups in the other source's vocabulary. One request (`type=TRUCK&per_page=20`) in Stage 1 settles it. Exclude it until then. |
| **ATVs: your call, I recommend exclude** | `ATV`, plus `DIRT BIKE` (off-road, usually not road-registrable in Poland, low value) |
| **Exclude** | `RECREATIONAL VEHICLE (RV)`, `TRAILERS`, `TRAILER`, `TRAVEL TRAILER`, `MOTOR HOME`, `MEDIUM DUTY/BOX TRUCKS`, `HEAVY DUTY TRUCKS`, `BUS`, `INDUSTRIAL EQUIPMENT`, `CONSTRUCTION EQUIPMENT`, `HEAVY EQUIPMENT`, `FARM EQUIPMENT`, `AGRICULTURE AND FARM EQUIPMENT`, `EMERGENCY EQUIPMENT`, `BOAT`, `JET SKI`, `PERSONAL WATERCRAFT`, `SNOWMOBILE`, `INSPECTION` (meaning unclear), `OTHER` |

### F3. Ended lots

- **`lot_sub_status` defaults to `Open`** ("active/open and not sold"); request 2 echoes `applied_filters.lot_sub_status: "Open"`. So **a query "without a state filter" returns only open lots**, and ended lots need an explicit `lot_sub_status=Ended` (values: `Open`, `Live`, `Ended`). `lot_status` (`All`/`Timed`/`Buy Now`) is the offering mode, not the result. The docs say: "Ended does not establish a sale."
- **Result:** `auction.outcome_status` is one of `Sold`, `Sold on Approval`, `Not Sold`, `Removed` or `null` ("explicit normalized outcome only; elapsed time alone is not treated as a sale"); there's also `auction.lot_removed`. `auction.state` values seen: `open`, `finished`, never `sold` or `timed`. Today's `mapStatus` maps `finished` → `UNSOLD`, even for the sold fixtures. Older records (the August 2026 fixture) have no `outcome_status`, only `last_sold_status: "Sold"`, so the mapping should fall back to that.
- **Final price:** `pricing.sale_price_usd` ("canonical persisted final/approval price"), with fallback `pricing.last_sold_price_usd`. `current_bid_usd` is not the result.
- **Sale date:** `auction.last_sold_at` (exact, nullable), else `auction.last_sold_day` (a date), else `auction.auction_ended_at` ("best known completed-auction/outcome timestamp", never a future date). `outcome_observed_at` is when Apibara saw the result.
- **`Sold on Approval`** isn't final: the seller can still refuse, Apibara leaves it out of its own price estimates, and `/changes` has the reason `awaiting_approval`. Proposal: don't map it to `SOLD`; keep the lot ended without a result until a final outcome arrives (or the expiry fallback kicks in).
- **Does `updated_at` change when the result is posted?** Yes: the sold fixture has `last_sold_at` 2026-09-09 18:30 and `updated_at` 2026-09-15 02:00. But `updated_within_minutes` only catches it when combined with `lot_sub_status=Ended`.
- **Does a live bid change `updated_at`?** Two requests can't prove it, but `updated_at` clearly moves for far more than material changes:
  - Request 2 counted **118,132** open records of the core types updated in 24 h, while Apibara's headline is "~30K added or refreshed daily" for everything.
  - The newest of them, an open lot 5 days before its auction, had an `updated_at` equal to the second of our request.

  Treat `updated_within_minutes` as "re-crawled", not "changed". `/changes` separates material events (`lot.*`, `auction.*`) from `bid.changed`/`buy_now.changed`.

### F4. Sorting, date filter, total

- **There's no `sort` parameter.** With `updated_within_minutes` the order is fixed: `updated_at` **descending** (`meta.updated_at_order: "desc"`), and the cursor is a keyset on `(updated_at, id)`. So Stage 2's "advance the watermark per page" variant is impossible; only "hold the watermark until the window is drained" works.
- **Auction-date range:** `auction_date_from` / `auction_date_to` (`Y-m-d`). There's also `upcoming=only|without|all` (threshold 30 min) and `today_only`.
- **Total:** `include_total=true` puts an exact count, cached briefly, in `meta.total`. Without it, `total` is null.

### F5. Volume and requests

Measured: **118,132** open (upcoming + live) records of the six core types with `updated_at` in the last 24 h (request 2, 2026-10-23 10:03 → 2026-10-24 10:03 UTC). Pickups, ended lots and `type: null` records aren't in this number.

| `per_page` (plan) | Requests/day | Requests/month (×30) |
|---|---|---|
| 20 (Test … Pro; ours) | 5,907 | ~177,000 |
| 30 (Pro+, Pro max) | 3,938 | ~118,000 |
| 40 (Mega, Mega max) | 2,954 | ~88,600 |

- **Free plan, `monthly-budget: 30`:** 600 records a month, about 0.5 % of **one** day's open updates. Coverage will always report a backlog ("Zaległości").
- **Basic** (30,000/month, $25): 600,000 records a month, about 17 % of the open updates.
- **Pro+** (300,000/month, $90, `per_page` 30) is the first plan where open-lot coverage via `updated_within_minutes` fits (~118k a month), with room left for ended lots and live lookups.
- Ended lots weren't measured. `/changes` (beta, see F7) would make them cheap: up to **500** events per request, against 20 records per page.

### F6. `platform` / `auction_type`, `make` / `model`

- **`platform` and `auction_type` can't be combined:** "Use either `auction_type` or `platform` in the same request". `auction_type=0` covers both platforms in one request; `platform` has no "all" value. For the sync, either one stream per platform (`platform=copart|iaai`) or one with `auction_type=0` works. Per-platform streams cost twice as many requests for small windows (a half-empty last page each).
- **`make`/`model` aren't needed for the sync:** the type filter replaces `apibara.targets`. They stay in the live lookups (`ApibaraController` search) and will be useful later for "sold prices of this model" (`/market/sold-prices`, not used yet). `findSoldComparables` uses our own database.

### F7. What this changes in Stages 1–2 (needs your OK)

1. **"Incremental without a state filter" isn't possible.** Each platform needs an **Open** query and a separate way to get outcomes.
2. **Outcomes: I propose `GET /changes?terminal=true`** (`lot.sold`, `lot.not_sold`, `lot.removed`) instead of a second `/vehicles?lot_sub_status=Ended` stream.
   - Up to 500 events per request.
   - Events come in `sequence` order with a durable cursor, so the watermark becomes an int64 `sequence`, advanced only after commit. `latest_available_sequence − cursor` gives the exact backlog.
   - Events carry only `platform`, `lot_number` and `vin`, plus `before`/`after`, with no type. So we apply them **only to lots we already store** and ignore the rest; no type filter is needed.
   - Caveats: it's **beta** (`schema_version 2026-10-04.beta1`); the replay window is 14 days (HTTP 410 `cursor_expired` → resync); plan availability isn't stated; and the `after` payload for a sale isn't documented field by field. One request in Stage 2 (costs 1) can verify it.
3. **Open lots:** since `updated_at` moves on every re-crawl, prioritize by auction date, not by watermark: `upcoming=only` + `auction_date_from=today` + `auction_date_to=today+backfill-upcoming-days`. That spends the budget on lots someone can still bid on. The `updated_within_minutes` watermark (held until drained, F4) only makes sense on a plan that can drain a whole day.
4. **Stream keys:** `copart:open`, `iaai:open`, `copart:outcomes`, `iaai:outcomes`. The type values go into one OR-ed request, not one stream per type.
5. **`mapStatus`:** `timed` → `UNSOLD` is indeed wrong, and so is `finished` → `UNSOLD`. Stage 1's mapping should read `outcome_status`, `last_sold_status` and `lot_removed`, not `state`.
